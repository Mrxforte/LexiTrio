"""Translate dictionary entries that are missing Russian/Uzbek meanings."""

from __future__ import annotations

from collections.abc import Sequence

from django.conf import settings
from django.db.models import Q

from learning.models import DictionaryEntry, Translation

from .professional import (
    context_note,
    format_translation,
    normalize_gender,
    normalize_pos,
)
from .translator import normalize_targets, translate_headwords, translate_professional


def format_professional_meaning(language: str, text: str, detail: dict) -> str:
    """Render ``primary_meaning_<lang>`` the way a paper dictionary does."""
    note = context_note(
        language,
        pos=normalize_pos(detail.get("pos")),
        gender=normalize_gender(detail.get("gender")),
    )
    body = format_translation(text, note)
    return f"{body} ({note})" if note and body else body


def count_candidates(languages: Sequence[str], *, only_missing: bool = True) -> int:
    return _candidate_queryset(languages, only_missing).count()


def backfill_entries(
    *,
    languages: str | Sequence[str] | None = None,
    limit: int = 20,
    only_missing: bool = True,
    dry_run: bool = False,
    professional: bool = False,
    entry_ids: Sequence[int] | None = None,
    cursor_id: int = 0,
    client=None,
    batch_size: int | None = None,
) -> dict:
    """Translate up to `limit` entries starting after `cursor_id`."""
    target_languages = normalize_targets(languages)
    queryset = _candidate_queryset(
        target_languages, only_missing, entry_ids=entry_ids, cursor_id=cursor_id
    )
    entries = list(queryset.prefetch_related("translations")[: max(1, int(limit))])

    summary = {
        "processed": 0,
        "translated": 0,
        "failed": 0,
        "skipped": 0,
        "updated": [],
        "nextCursor": cursor_id,
        "model": "",
        "warnings": [],
    }
    if not entries:
        return summary

    summary["nextCursor"] = entries[-1].id
    summary["processed"] = len(entries)

    pending_by_index: dict[int, list[str]] = {}
    for index, entry in enumerate(entries):
        pending = [
            language
            for language in target_languages
            if not only_missing or not getattr(entry, f"primary_meaning_{language}").strip()
        ]
        if pending:
            pending_by_index[index] = pending
        else:
            summary["skipped"] += 1

    if not pending_by_index:
        return summary

    groups: dict[tuple[str, ...], list[int]] = {}
    for index, languages_for_entry in pending_by_index.items():
        groups.setdefault(tuple(languages_for_entry), []).append(index)

    translated: dict[int, dict[str, str]] = {}
    professional_data: dict[int, dict] = {}
    for group_languages, indexes in groups.items():
        payloads = [
            {
                "headword": entries[index].headword,
                "definition": entries[index].primary_meaning_en,
            }
            for index in indexes
        ]
        if professional:
            records, model, warnings = translate_professional(
                payloads,
                list(group_languages),
                client=client,
                batch_size=batch_size,
            )
        else:
            records, model, warnings = translate_headwords(
                payloads,
                list(group_languages),
                client=client,
                batch_size=batch_size,
            )

        summary["model"] = model or summary["model"]
        summary["warnings"].extend(warnings)
        for index, record in zip(indexes, records):
            if professional:
                values = {}
                for language in group_languages:
                    detail = record.get(language) or {}
                    text = str(detail.get("text", "")).strip()
                    if text:
                        values[language] = format_professional_meaning(language, text, detail)
                if values:
                    translated[index] = values
                    professional_data[index] = record
                else:
                    summary["failed"] += 1
                continue

            values = {
                language: str(record.get(language, "")).strip()
                for language in group_languages
                if str(record.get(language, "")).strip()
            }
            if values:
                translated[index] = values
            else:
                summary["failed"] += 1

    if not translated:
        return summary

    if dry_run:
        summary["translated"] = len(translated)
        summary["updated"] = [
            {
                "id": entries[index].id,
                "headword": entries[index].headword,
                **translated[index],
            }
            for index in sorted(translated)
        ]
        return summary

    changed_entries: list[DictionaryEntry] = []
    new_translations: list[Translation] = []
    max_length = Translation._meta.get_field("translation_text").max_length or 300

    for index in sorted(translated):
        entry = entries[index]
        changed_fields = []
        if professional:
            record = professional_data.get(index) or {}
            pos = normalize_pos(record.get("pos")) or entry.part_of_speech
            ipa = str(record.get("ipa", "")).strip()
            article = str(record.get("article", "")).strip()
            plural = str(record.get("plural", "")).strip()
            if ipa and not entry.phonetic_ipa.strip():
                entry.phonetic_ipa = ipa[:200]
                changed_fields.append("phonetic_ipa")
            if pos and pos != entry.part_of_speech:
                entry.part_of_speech = pos[:40]
                changed_fields.append("part_of_speech")
            if article and not entry.article:
                entry.article = article[:8]
                changed_fields.append("article")
            if plural and not (entry.plural_form or entry.comparative_form):
                if pos == "adjective":
                    entry.comparative_form = plural[:200]
                    changed_fields.append("comparative_form")
                else:
                    entry.plural_form = plural[:200]
                    changed_fields.append("plural_form")

        for language, value in translated[index].items():
            field_name = f"primary_meaning_{language}"
            if only_missing and getattr(entry, field_name).strip():
                continue
            setattr(entry, field_name, value)
            changed_fields.append(field_name)
            detail = (professional_data.get(index) or {}).get(language) or {}
            if not any(
                item.target_language == language for item in entry.translations.all()
            ):
                note = context_note(
                    language,
                    pos=normalize_pos(detail.get("pos")) or entry.part_of_speech,
                    gender=normalize_gender(detail.get("gender")),
                )
                new_translations.append(
                    Translation(
                        entry=entry,
                        target_language=language,
                        translation_text=value[:max_length],
                        context_note=note,
                        part_of_speech=(normalize_pos(detail.get("pos")) or entry.part_of_speech)[:40],
                        gender=normalize_gender(detail.get("gender"))[:8],
                        quality=Translation.Quality.MACHINE,
                    )
                )
        if changed_fields:
            entry.save(update_fields=changed_fields)
            changed_entries.append(entry)

    if new_translations:
        Translation.objects.bulk_create(new_translations, batch_size=500)

    summary["translated"] = len(changed_entries)
    summary["updated"] = [
        {
            "id": entry.id,
            "headword": entry.headword,
            **{
                language: getattr(entry, f"primary_meaning_{language}")
                for language in target_languages
            },
        }
        for entry in changed_entries
    ]
    return summary


def _candidate_queryset(
    languages: Sequence[str],
    only_missing: bool,
    *,
    entry_ids: Sequence[int] | None = None,
    cursor_id: int = 0,
):
    queryset = DictionaryEntry.objects.filter(language="en")
    if entry_ids:
        queryset = queryset.filter(id__in=list(entry_ids))
    elif cursor_id:
        queryset = queryset.filter(id__gt=cursor_id)

    if only_missing:
        condition = Q()
        for language in languages:
            condition |= Q(**{f"primary_meaning_{language}": ""})
        queryset = queryset.filter(condition)

    return queryset.order_by("id")
