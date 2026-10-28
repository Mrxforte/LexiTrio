"""Build the translation client from one or more configured providers.

DeepSeek, Gemini (OpenAI-compatible endpoint) and OpenAI all speak the same
``/chat/completions`` protocol, so providers are just (key, base_url, model)
triples. When more than one is configured the client rotates between them, which
both multiplies throughput and spreads free-tier quotas.
"""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass

from django.conf import settings

from .openai_client import (
    ChatResult,
    OpenAIClient,
    TranslationNotConfigured,
    TranslationProviderError,
)


@dataclass(frozen=True)
class ProviderConfig:
    name: str
    api_key: str
    base_url: str
    model: str


_DISABLED_KEY_VALUES = {"", "none", "null", "disabled", "off"}


def _is_enabled_key(value: str) -> bool:
    return str(value or "").strip().lower() not in _DISABLED_KEY_VALUES


def configured_providers() -> list[ProviderConfig]:
    """Return every provider that has an API key, in configuration order."""
    providers: list[ProviderConfig] = []

    primary_key = (getattr(settings, "OPENAI_API_KEY", "") or "").strip()
    if _is_enabled_key(primary_key):
        providers.append(
            ProviderConfig(
                name="gemini" if "googleapis.com" in settings.OPENAI_BASE_URL else "openai",
                api_key=primary_key,
                base_url=settings.OPENAI_BASE_URL,
                model=settings.OPENAI_MODEL,
            )
        )

    deepseek_key = (getattr(settings, "DEEPSEEK_API_KEY", "") or "").strip()
    if _is_enabled_key(deepseek_key):
        providers.append(
            ProviderConfig(
                name="deepseek",
                api_key=deepseek_key,
                base_url=settings.DEEPSEEK_BASE_URL,
                model=settings.DEEPSEEK_MODEL,
            )
        )

    return providers


class RotatingClient:
    """Round-robin over providers so every one of them does useful work.

    A provider that fails is benched for a cooldown window, so a quota-exhausted
    API does not slow down every single batch with its retries.
    """

    def __init__(self, clients: list[OpenAIClient], cooldown: float | None = None):
        self._clients = clients
        self._lock = threading.Lock()
        self._cursor = 0
        self._cooldown = float(
            getattr(settings, "TRANSLATION_PROVIDER_COOLDOWN", 300)
            if cooldown is None
            else cooldown
        )
        self._benched_until: dict[int, float] = {}

    @property
    def model(self) -> str:
        return "+".join(client.model for client in self._clients)

    @property
    def names(self) -> list[str]:
        return [client.model for client in self._clients]

    def _order(self) -> list[tuple[int, OpenAIClient]]:
        now = time.monotonic()
        with self._lock:
            start = self._cursor % len(self._clients)
            self._cursor += 1
            rotated = [
                ((start + offset) % len(self._clients))
                for offset in range(len(self._clients))
            ]
            ready = [i for i in rotated if self._benched_until.get(i, 0) <= now]
            cooling = [i for i in rotated if self._benched_until.get(i, 0) > now]
        # Only fall back to benched providers when nothing else is available:
        # retrying a quota-exhausted API costs a full timeout per batch.
        ordered = ready or cooling
        return [(index, self._clients[index]) for index in ordered]

    def _bench(self, index: int) -> None:
        with self._lock:
            self._benched_until[index] = time.monotonic() + self._cooldown

    def _release(self, index: int) -> None:
        with self._lock:
            self._benched_until.pop(index, None)

    def complete(
        self, *, system_prompt: str, user_prompt: str, json_mode: bool | None = None
    ) -> ChatResult:
        errors: list[str] = []
        for index, client in self._order():
            try:
                result = client.complete(
                    system_prompt=system_prompt, user_prompt=user_prompt, json_mode=json_mode
                )
            except TranslationNotConfigured:
                continue
            except TranslationProviderError as exc:
                self._bench(index)
                errors.append(f"{client.model}: {exc}")
                continue
            self._release(index)
            return result
        if errors:
            raise TranslationProviderError(
                "Barcha tarjima provayderlari band: " + "; ".join(errors)
            )
        raise TranslationNotConfigured("Tarjima provayderi sozlanmagan.")


def build_translation_client():
    """Single client when one provider is configured, rotating client otherwise."""
    providers = configured_providers()
    if not providers:
        return OpenAIClient()  # raises TranslationNotConfigured with a clear message

    clients = [
        OpenAIClient(
            api_key=provider.api_key,
            base_url=provider.base_url,
            model=provider.model,
        )
        for provider in providers
    ]
    if len(clients) == 1:
        return clients[0]
    return RotatingClient(clients)
