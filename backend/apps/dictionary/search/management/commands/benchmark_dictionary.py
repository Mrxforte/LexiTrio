"""Measure dictionary search latency on the real catalog.

Run it before and after a change to compare like for like:

    backend\\.venv\\Scripts\\python.exe backend\\manage.py benchmark_dictionary

The command never fabricates numbers: it times the exact code path the API
uses and prints what it measured.
"""

import statistics
import time

from django.core.management.base import BaseCommand, CommandError

from apps.dictionary.entries.models import DictionaryEntry, Translation
from apps.dictionary.search import engine as search_module
from apps.dictionary.search.engine import SearchRequest, search

DEFAULT_QUERIES = [
    ("apple", "exact word"),
    ("app", "prefix"),
    ("olma", "uzbek translation"),
    ("яблоко", "russian word"),
    ("pple", "substring"),
    ("look after", "multi word"),
]


class Command(BaseCommand):
    help = "Lug'at qidiruvining haqiqiy kechikishini o'lchaydi (FTS5 va ORM)."

    def add_arguments(self, parser):
        parser.add_argument("--repeat", type=int, default=5)
        parser.add_argument("--page-size", type=int, default=20)
        parser.add_argument(
            "--query",
            action="append",
            dest="queries",
            help="Qo'shimcha qidiruv so'zi (bir necha marta berilishi mumkin).",
        )

    def handle(self, *args, **options):
        repeat = max(1, options["repeat"])
        page_size = max(1, options["page_size"])
        queries = list(DEFAULT_QUERIES)
        for extra in options["queries"] or []:
            queries.append((extra, "custom"))

        search_module.reset_search_caches()
        index_ready = search_module.fts_table_has_rows()

        self.stdout.write(
            f"Baza: {DictionaryEntry.objects.count()} yozuv, "
            f"{Translation.objects.count()} tarjima, "
            f"indeks: {search_module.FTS_TABLE} "
            f"({'bor' if index_ready else 'yoq'})"
        )
        if not index_ready:
            raise CommandError(
                "FTS5 indeksi bosh. Avval `manage.py rebuild_search_index` ni ishga tushiring."
            )
        self.stdout.write("")
        self.stdout.write(
            f"{'query':16} {'kind':16} {'backend':9} {'median ms':>10} {'min ms':>8} {'rows':>5}"
        )

        for query, kind in queries:
            request = SearchRequest(query=query)
            for backend in ("fts5", "orm"):
                timings = []
                rows = 0
                actual_backend = "-"
                original = search_module._has_fts_table
                if backend == "orm":
                    search_module._has_fts_table = lambda: False
                try:
                    for _ in range(repeat):
                        started = time.perf_counter()
                        result = search(request, limit=page_size, offset=0)
                        timings.append((time.perf_counter() - started) * 1000)
                        rows = len(result.entry_ids)
                        actual_backend = result.backend
                finally:
                    search_module._has_fts_table = original
                self.stdout.write(
                    f"{query[:16]:16} {kind:16} {actual_backend:9} "
                    f"{statistics.median(timings):10.2f} {min(timings):8.2f} {rows:5}"
                )

        self.stdout.write("")
        self.stdout.write(self.style.SUCCESS("Olchov tugadi."))
