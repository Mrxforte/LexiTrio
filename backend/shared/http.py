"""HTTP helpers: query-string parsing, validators and conditional-request support."""

import hashlib
from datetime import timezone as dt_timezone
from urllib.parse import urlencode

from django.conf import settings
from django.utils import timezone
from django.utils.dateparse import parse_datetime
from rest_framework.exceptions import ValidationError

from .text import normalize_text


def query_int(request, name, default, maximum=None):
    raw = request.query_params.get(name)
    if raw in (None, ""):
        return default
    try:
        value = int(raw)
    except (TypeError, ValueError) as exc:
        raise ValidationError({name: "Butun son bo'lishi kerak."}) from exc
    if value < 0:
        raise ValidationError({name: "Manfiy bo'lishi mumkin emas."})
    return min(value, maximum) if maximum is not None else value


def bool_param(request, name, *, default: bool) -> bool:
    raw = request.query_params.get(name)
    if raw is None or raw == "":
        return default
    return raw.strip().lower() not in {"0", "false", "no", "off"}


def language_param(request, name):
    raw = (request.query_params.get(name) or "").strip().lower()
    if not raw:
        return None
    normalized = normalize_text(raw)
    if not all(part.isalnum() or part == "-" for part in normalized.split("-")):
        raise ValidationError({name: "Til kodi noto'g'ri."})
    return normalized


def parse_since(raw):
    if not raw:
        return None
    parsed = parse_datetime(raw)
    if parsed is None:
        raise ValidationError({"since": "ISO 8601 sana formati kutilgan."})
    if timezone.is_naive(parsed):
        parsed = parsed.replace(tzinfo=dt_timezone.utc)
    return parsed


def page_params(request):
    page = max(1, query_int(request, "page", 1))
    page_size = max(
        1,
        query_int(
            request,
            "pageSize",
            settings.DICTIONARY_SEARCH_DEFAULT_PAGE_SIZE,
            settings.DICTIONARY_SEARCH_MAX_PAGE_SIZE,
        ),
    )
    return page, page_size


def page_url(request, page: int) -> str:
    params = request.query_params.copy()
    params["page"] = page
    return request.build_absolute_uri(request.path + "?" + urlencode(params, doseq=True))


def etag_for(cache_key: str) -> str:
    return '"' + hashlib.sha256(cache_key.encode("utf-8")).hexdigest()[:32] + '"'


def etag_matches(request, etag: str) -> bool:
    """Whether ``If-None-Match`` names ``etag``; weak validators (gzip adds ``W/``) count."""
    header = request.headers.get("If-None-Match")
    if not header:
        return False
    candidates = [part.strip().removeprefix("W/") for part in header.split(",")]
    return "*" in candidates or etag in candidates
