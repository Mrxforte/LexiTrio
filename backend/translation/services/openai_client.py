"""Minimal OpenAI-compatible chat-completions client used for dictionary translation."""

from __future__ import annotations

import json
import time
from dataclasses import dataclass

import httpx
from django.conf import settings


class TranslationProviderError(Exception):
    """The translation provider failed or returned unusable data."""


class TranslationNotConfigured(TranslationProviderError):
    """No provider credentials are configured."""


@dataclass(frozen=True)
class ChatResult:
    content: str
    model: str


def _provider_error_message(response: httpx.Response) -> str:
    try:
        payload = response.json()
    except ValueError:
        return (response.text or "").strip()[:300]
    error = payload.get("error") if isinstance(payload, dict) else None
    if isinstance(error, dict) and error.get("message"):
        return str(error["message"])[:300]
    return json.dumps(payload)[:300]


class OpenAIClient:
    """Thin wrapper around `POST {base_url}/chat/completions` with retries."""

    def __init__(self, *, api_key=None, base_url=None, model=None, timeout=None, max_retries=None):
        configured_key = api_key if api_key is not None else getattr(settings, "OPENAI_API_KEY", "")
        self.api_key = (configured_key or "").strip()
        self.base_url = (
            base_url or getattr(settings, "OPENAI_BASE_URL", "https://api.openai.com/v1")
        ).rstrip("/")
        self.model = model or getattr(settings, "OPENAI_MODEL", "gpt-4o-mini")
        configured_timeout = timeout if timeout is not None else getattr(settings, "OPENAI_TIMEOUT", 90)
        self.timeout = float(configured_timeout)
        retries = max_retries if max_retries is not None else getattr(settings, "OPENAI_MAX_RETRIES", 2)
        self.max_retries = max(0, int(retries))

    def complete(self, *, system_prompt: str, user_prompt: str, json_mode: bool = True) -> ChatResult:
        if not self.api_key:
            raise TranslationNotConfigured(
                "OPENAI_API_KEY sozlanmagan. Tarjima uchun backend/.env ga kalit qo'shing."
            )

        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": float(getattr(settings, "OPENAI_TEMPERATURE", 0.2)),
            "max_tokens": int(getattr(settings, "OPENAI_MAX_OUTPUT_TOKENS", 8192)),
        }
        if json_mode:
            payload["response_format"] = {"type": "json_object"}

        last_error: Exception | None = None
        for attempt in range(self.max_retries + 1):
            try:
                with httpx.Client(timeout=self.timeout) as client:
                    response = client.post(
                        f"{self.base_url}/chat/completions",
                        headers={
                            "Authorization": f"Bearer {self.api_key}",
                            "Content-Type": "application/json",
                        },
                        json=payload,
                    )
            except httpx.TimeoutException:
                last_error = TranslationProviderError("Tarjima xizmati javob bermadi (timeout).")
            except httpx.HTTPError as exc:
                last_error = TranslationProviderError(f"Tarjima xizmatiga ulanib bo'lmadi: {exc}")
            else:
                if response.status_code == 401:
                    raise TranslationProviderError("OpenAI API kaliti qabul qilinmadi (401).")
                if response.status_code == 429 or response.status_code >= 500:
                    last_error = TranslationProviderError(
                        f"Tarjima xizmati band ({response.status_code})."
                    )
                elif response.status_code >= 400:
                    raise TranslationProviderError(
                        f"OpenAI xatosi ({response.status_code}): {_provider_error_message(response)}"
                    )
                else:
                    return self._parse_response(response)

            if attempt < self.max_retries:
                time.sleep(min(2**attempt, 8))

        raise last_error or TranslationProviderError("Tarjima xizmati javob bermadi.")

    def _parse_response(self, response: httpx.Response) -> ChatResult:
        try:
            data = response.json()
        except ValueError as exc:
            raise TranslationProviderError("Tarjima xizmati JSON bo'lmagan javob qaytardi.") from exc
        choices = data.get("choices") or []
        message = (choices[0].get("message") if choices else {}) or {}
        content = (message.get("content") or "").strip()
        if not content:
            raise TranslationProviderError("Tarjima xizmati bo'sh javob qaytardi.")
        return ChatResult(content=content, model=data.get("model") or self.model)
