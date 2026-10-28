"""Search endpoints: ranked search, and the entry list that answers ``?q=`` through the same index."""

from django.conf import settings
from rest_framework import permissions, status
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView

from apps.dictionary.entries.cache import safe_cache_get_or_set
from apps.dictionary.entries.serializers import DictionaryEntrySerializer
from apps.dictionary.entries.views import DictionaryEntryBrowseViewSet
from shared.http import (
    bool_param,
    etag_for,
    etag_matches,
    language_param,
    page_params,
    page_url,
)

from .engine import SearchRequest
from .serializers import DictionarySearchResultSerializer
from .services import run_search, search_cache_key


def search_response(request, search_request: SearchRequest, *, serializer_class):
    """Ranked, paged search response with ETag support and a version-keyed result cache."""
    page, page_size = page_params(request)
    key = search_cache_key(search_request, page, page_size, serializer_class.__name__)

    def compute_body():
        result = run_search(search_request, page=page, page_size=page_size)
        body = {
            "query": search_request.query.strip(),
            "sourceLanguage": search_request.source_language,
            "targetLanguage": search_request.target_language,
            "partOfSpeech": search_request.part_of_speech,
            "page": page,
            "pageSize": page_size,
            "hasMore": result.has_more,
            "next": page_url(request, page + 1) if result.has_more else None,
            "previous": page_url(request, page - 1) if page > 1 else None,
            "backend": result.backend,
            "tookMs": result.took_ms,
            "results": serializer_class(result.entries, many=True).data,
        }
        return (etag_for(key), body)

    if settings.DICTIONARY_SEARCH_CACHE_TTL:
        etag, body = safe_cache_get_or_set(key, compute_body, settings.DICTIONARY_SEARCH_CACHE_TTL)
    else:
        etag, body = compute_body()

    headers = {
        "ETag": etag,
        "Cache-Control": f"public, max-age={max(0, settings.DICTIONARY_VERSION_CACHE_TTL)}",
    }
    if etag_matches(request, etag):
        return Response(status=status.HTTP_304_NOT_MODIFIED, headers=headers)
    return Response(body, headers=headers)


class DictionaryEntryViewSet(DictionaryEntryBrowseViewSet):
    """Entry list/detail; a free-text ``q`` switches the list to the ranked search index."""

    def list(self, request, *args, **kwargs):
        query = (request.query_params.get("q") or "").strip()
        if not query:
            return super().list(request, *args, **kwargs)
        return search_response(
            request,
            SearchRequest(
                query=query,
                source_language=request.query_params.get("language") or None,
                target_language=request.query_params.get("target") or None,
                part_of_speech=request.query_params.get("pos") or None,
                cefr_level=request.query_params.get("level") or None,
            ),
            serializer_class=DictionaryEntrySerializer,
        )


class DictionarySearchView(APIView):
    """Ranked, paged dictionary search.

    Query parameters:
        ``q``        free text (required). Matched against headwords, primary
                     meanings, alternative spellings and translations.
        ``source``   entry language (``en``/``ru``/``uz``/``uz-cyrl``).
        ``target``   keep only entries that have a translation in this language.
        ``pos``      part of speech (``noun``, ``verb``, ...).
        ``level``    CEFR level (``A1`` .. ``C2``).
        ``partial``  ``false`` disables the substring fallback.
        ``page``     1-based page number.
        ``pageSize`` rows per page, capped at ``DICTIONARY_SEARCH_MAX_PAGE_SIZE``.

    Responses carry an ``ETag`` derived from the current dictionary version, so
    a client that sends ``If-None-Match`` gets a ``304`` instead of a payload.
    """

    permission_classes = [permissions.AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "dictionary_search"

    def get(self, request):
        query = (request.query_params.get("q") or "").strip()
        if not query:
            raise ValidationError({"q": "Qidiruv so'zi kiritilishi shart."})
        return search_response(
            request,
            SearchRequest(
                query=query,
                source_language=language_param(request, "source"),
                target_language=language_param(request, "target"),
                part_of_speech=request.query_params.get("pos") or None,
                cefr_level=request.query_params.get("level") or None,
                match_partial=bool_param(request, "partial", default=True),
            ),
            serializer_class=DictionarySearchResultSerializer,
        )
