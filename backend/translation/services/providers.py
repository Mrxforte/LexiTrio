"""Build the translation client from one or more configured providers.

DeepSeek, Gemini (OpenAI-compatible endpoint) and OpenAI all speak the same
``/chat/completions`` protocol, so providers are just (key, base_url, model)
triples. When more than one is configured the client rotates between them, which
both multiplies throughput and spreads free-tier quotas.
"""

from __future__ import annotations

import threading
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


def configured_providers() -> list[ProviderConfig]:
    """Return every provider that has an API key, in configuration order."""
    providers: list[ProviderConfig] = []

    primary_key = (getattr(settings, "OPENAI_API_KEY", "") or "").strip()
    if primary_key:
        providers.append(
            ProviderConfig(
                name="gemini" if "googleapis.com" in settings.OPENAI_BASE_URL else "openai",
                api_key=primary_key,
                base_url=settings.OPENAI_BASE_URL,
                model=settings.OPENAI_MODEL,
            )
        )

    deepseek_key = (getattr(settings, "DEEPSEEK_API_KEY", "") or "").strip()
    if deepseek_key:
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
    """Round-robin over providers so every one of them does useful work."""

    def __init__(self, clients: list[OpenAIClient]):
        self._clients = clients
        self._lock = threading.Lock()
        self._cursor = 0

    @property
    def model(self) -> str:
        return "+".join(client.model for client in self._clients)

    @property
    def names(self) -> list[str]:
        return [client.model for client in self._clients]

    def _order(self) -> list[OpenAIClient]:
        with self._lock:
            start = self._cursor % len(self._clients)
            self._cursor += 1
        return self._clients[start:] + self._clients[:start]

    def complete(
        self, *, system_prompt: str, user_prompt: str, json_mode: bool | None = None
    ) -> ChatResult:
        errors: list[str] = []
        for client in self._order():
            try:
                return client.complete(
                    system_prompt=system_prompt, user_prompt=user_prompt, json_mode=json_mode
                )
            except TranslationNotConfigured:
                continue
            except TranslationProviderError as exc:
                errors.append(f"{client.model}: {exc}")
                continue
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
