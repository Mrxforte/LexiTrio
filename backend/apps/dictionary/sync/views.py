"""Dictionary sync endpoints: paged delta export and batched word details."""

from django.utils import timezone
from rest_framework import permissions
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView

from apps.dictionary.entries.serializers import (
    CollocationSerializer,
    DictionaryEntrySerializer,
    ExampleSentenceSerializer,
    SynonymAntonymSerializer,
    TranslationSerializer,
    WordFormSerializer,
    WordMeaningSerializer,
)
from shared.http import bool_param, parse_since, query_int

from . import services


def _child_payload(entry_ids, meaning_ids):
    rows = services.fetch_child_rows(entry_ids, meaning_ids)
    return {
        "translations": TranslationSerializer(rows["translations"], many=True).data,
        "examples": ExampleSentenceSerializer(rows["examples"], many=True).data,
        "synonyms": SynonymAntonymSerializer(rows["synonyms"], many=True).data,
        "forms": WordFormSerializer(rows["forms"], many=True).data,
        "collocations": CollocationSerializer(rows["collocations"], many=True).data,
    }


class DictionarySyncView(APIView):
    """Paged, resumable dictionary export used by the Android offline cache.

    Query parameters:
        ``afterId`` - cursor; only rows with a greater id are returned.
        ``limit``   - page size, capped at 500.
        ``since``   - ISO 8601 timestamp; returns rows changed after it
                      (entry itself or any of its meanings). The first page of
                      such a pass also lists ``deletedIds``; a ``since`` older
                      than the tombstone retention yields a full pass flagged
                      ``fullResync``.
        ``lite``    - ``1`` returns only entries and meanings (everything the
                      offline search needs) in pages of up to
                      ``MAX_LITE_PAGE_SIZE`` rows. Translations, examples and
                      the like are then fetched on demand.
    """

    permission_classes = [permissions.AllowAny]
    # Bulk download; the shared anonymous rate would need minutes per corpus.
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "dictionary_sync"

    def get(self, request):
        lite = bool_param(request, "lite", default=False)
        limit = max(
            1,
            query_int(request, "limit", services.DEFAULT_PAGE_SIZE, services.max_page_size(lite)),
        )
        after_id = query_int(request, "afterId", 0)
        since = parse_since(request.query_params.get("since"))

        page = services.fetch_sync_page(after_id=after_id, limit=limit, since=since)

        payload = {
            "entries": DictionaryEntrySerializer(page.entries, many=True).data,
            "meanings": WordMeaningSerializer(page.meanings, many=True).data,
        }
        if not lite:
            payload.update(_child_payload(page.entry_ids, page.meaning_ids))
        payload.update(
            {
                "nextAfterId": page.next_after_id,
                "hasMore": page.has_more,
                "serverTime": timezone.now().isoformat(),
            }
        )
        if after_id == 0:
            payload["dictionary"] = services.dictionary_meta()
            if page.deleted_ids is not None:
                payload["deletedIds"] = page.deleted_ids
                payload["deletedIdsTruncated"] = page.deleted_ids_truncated
        if page.full_resync:
            payload["fullResync"] = True
        return Response(payload)


class DictionaryDetailsView(APIView):
    """Everything a word detail screen needs, for a handful of ids at once.

    ``GET /dictionary/details/?ids=1,2,3`` returns meanings, translations,
    examples, synonyms, forms and collocations for up to
    ``MAX_DETAIL_IDS`` entries in one request (no per-word round trips).
    """

    permission_classes = [permissions.AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "dictionary_sync"

    def get(self, request):
        ids = self._parse_ids(request.query_params.get("ids"))
        details = services.fetch_word_details(ids)
        entry_ids = [entry.id for entry in details.entries]
        meaning_ids = [meaning.id for meaning in details.meanings]
        return Response(
            {
                "entries": DictionaryEntrySerializer(details.entries, many=True).data,
                "meanings": WordMeaningSerializer(details.meanings, many=True).data,
                **_child_payload(entry_ids, meaning_ids),
                "missingIds": details.missing_ids,
                "serverTime": timezone.now().isoformat(),
            }
        )

    @staticmethod
    def _parse_ids(raw):
        raw = (raw or "").strip()
        if not raw:
            raise ValidationError({"ids": "Kamida bitta id kerak."})
        try:
            ids = sorted({int(part) for part in raw.split(",") if part.strip()})
        except ValueError as exc:
            raise ValidationError({"ids": "Butun sonlar ro'yxati bo'lishi kerak."}) from exc
        if not ids or ids[0] < 1:
            raise ValidationError({"ids": "Id musbat butun son bo'lishi kerak."})
        if len(ids) > services.MAX_DETAIL_IDS:
            raise ValidationError(
                {"ids": f"Bir so'rovda {services.MAX_DETAIL_IDS} tadan ko'p id mumkin emas."}
            )
        return ids
