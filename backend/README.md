# LexiTrio Django API

The backend is a Django REST API. The Android Room database remains the local
source of truth so dictionary lookup, lessons, flashcards, and quizzes continue
to work without a network connection.

## Run locally on Windows

From the repository root:

```powershell
py -3.12 -m venv backend\.venv
backend\.venv\Scripts\python.exe -m pip install -r backend\requirements.txt
$env:DJANGO_DEBUG = "true"
$env:DJANGO_SECRET_KEY = "local-development-key"
backend\.venv\Scripts\python.exe backend\manage.py migrate
backend\.venv\Scripts\python.exe backend\manage.py import_catalog backend\catalog_seed.json
backend\.venv\Scripts\python.exe backend\manage.py runserver 0.0.0.0:8000
```

Set `DJANGO_SECRET_KEY`, `DJANGO_DEBUG`, and `DJANGO_ALLOWED_HOSTS` through
environment variables before deploying. `DATABASE_URL` enables PostgreSQL;
without it the project uses SQLite for local development.

## API

- `POST /api/v1/auth/register/` — `{ "email": "...", "password": "..." }`
- `POST /api/v1/auth/login/` — returns an API token
- `POST /api/v1/auth/logout/` — revokes the current token
- `GET /api/v1/dictionary/search/?q=...&source=en&target=uz&page=1&pageSize=20` —
  ranked search (see [Dictionary search](#dictionary-search))
- `GET /api/v1/dictionary/entries/?q=...&language=en` — same ranking, entry payload
- `GET /api/v1/catalog/` — learning content snapshot plus dictionary metadata
- `GET /api/v1/dictionary/sync/?afterId=0&limit=500[&since=ISO8601]` — paged,
  resumable dictionary export used by the Android offline cache
- `GET /api/v1/dictionary/{meanings,translations,examples,synonyms,forms,collocations}/`
- `GET /api/v1/{topics,topic-words,conversations,conversation-turns,grammar-lessons,quiz-questions}/`
- `GET|POST /api/v1/sync/` — authenticated user collections, saved words,
  quiz attempts, learning progress, and app preferences
- `GET /api/v1/translation/status/` — translation provider configuration and
  dictionary coverage
- `POST /api/v1/translation/text/` — translate plain text into Russian and Uzbek
- `POST /api/v1/translation/document/` — extract text from PDF/DOCX/TXT/CSV/MD
  and translate it
- `POST /api/v1/translation/backfill/` — translate existing dictionary entries
  (staff only)
- `GET|POST /api/v1/translation/jobs/` — queued background translation jobs
  (staff only)

Send `Authorization: Token <token>` for `/sync/`. The sync endpoint is
idempotent for client-generated UUIDs and scopes all user data to the
authenticated account. Content endpoints are read-only and public.

### Pagination

Catalog list endpoints return `{ "count", "next", "previous", "results" }` with
`page` and `pageSize` query parameters (default 50, max 500). Before this
change they returned every row at once, which for `/dictionary/entries/` meant
streaming 130k records; clients that expected a bare array must read
`results`.

### Dictionary search

`GET /api/v1/dictionary/search/` is served from a SQLite FTS5 index and keeps
response sizes bounded:

| Parameter | Meaning |
| --- | --- |
| `q` | required free text; matches headwords, primary meanings, alternative spellings and translations |
| `source` | entry language (`en`, `ru`, `uz`, `uz-cyrl`) |
| `target` | only entries that have a translation in this language |
| `pos` | part of speech |
| `level` | CEFR level |
| `partial` | `false` disables the substring fallback |
| `page`, `pageSize` | pagination, `pageSize` capped at `DICTIONARY_SEARCH_MAX_PAGE_SIZE` |

Results are ranked: exact headword, headword prefix, token matches in meanings
and translations (bm25), then substring matches. Because a total count would
force a full scan, the response reports `hasMore` plus `next`/`previous`
instead of `count`:

```json
{
  "query": "apple",
  "sourceLanguage": null,
  "targetLanguage": "uz",
  "page": 1,
  "pageSize": 20,
  "hasMore": true,
  "next": "https://host/api/v1/dictionary/search/?q=apple&page=2",
  "previous": null,
  "backend": "fts5",
  "tookMs": 1.8,
  "results": [
    {
      "id": 2461,
      "word": "apple",
      "sourceLanguage": "en",
      "partOfSpeech": "noun",
      "phoneticIpa": "/ˈæpəl/",
      "cefrLevel": "B2",
      "frequencyRank": 476,
      "article": "an",
      "pluralForm": "apples",
      "primaryMeaningRu": "яблоко (сущ.)",
      "primaryMeaningUz": "olma (ot)",
      "translations": [
        {
          "id": 311,
          "text": "olma",
          "targetLanguage": "uz",
          "partOfSpeech": "noun",
          "quality": "wiktionary"
        }
      ]
    }
  ]
}
```

Ranked search returns at most 1000 candidates per query; narrower queries
(`source`, `target`, `pos`, `level`) are the way to page deeper.

`backend` reports which engine answered: `fts5` for the index,
`orm` for the portable fallback used on PostgreSQL or before the index is
built.

### Search index maintenance

The index (`dictionary_search`) is created by migration
`learning.0008_dictionary_search_index` and kept in sync by triggers on
`learning_dictionaryentry` and `learning_translation`, so admin edits,
imports and translation jobs are searchable immediately.

```powershell
# rebuild or verify (safe to re-run at any time)
backend\.venv\Scripts\python.exe backend\manage.py rebuild_search_index
backend\.venv\Scripts\python.exe backend\manage.py rebuild_search_index --check

# data report: duplicate headwords, untranslated rows, orphan rows, index drift
backend\.venv\Scripts\python.exe backend\manage.py check_dictionary_integrity

# measured latency of the FTS5 path versus the ORM fallback
backend\.venv\Scripts\python.exe backend\manage.py benchmark_dictionary --repeat 5
```

Measured on the bundled 130,777-entry / 85,232-translation database
(Windows, SQLite 3.45.3, page size 20, median of 5 runs):

| Query | Kind | FTS5 | ORM fallback (previous behaviour) |
| --- | --- | --- | --- |
| `apple` | exact word | 0.89 ms | 201.94 ms |
| `app` | prefix | 3.98 ms | 181.56 ms |
| `olma` | Uzbek translation | 13.02 ms | 196.12 ms |
| `яблоко` | Russian word | 13.83 ms | 179.44 ms |
| `pple` | substring | 2.57 ms | 353.27 ms |
| `look after` | multi word | 16.72 ms | 219.15 ms |

End-to-end HTTP timings for the same database: a cache miss (`?q=apple` with
the version stamp caching disabled) takes ~25 ms including Django
serialization; a warm response is served from the cache in under 1 ms, and a
conditional request returns `304` with an empty body in ~0.7 ms. A cache miss
runs 13 SQL queries, of which 9 are the version stamp; with the stamp cached
that drops to 4.

### Caching

- Search responses carry `ETag` and `Cache-Control: public, max-age=<version TTL>`.
  Send `If-None-Match` to receive `304`.
- Rendered responses are cached in Django's cache for
  `DICTIONARY_SEARCH_CACHE_TTL` seconds (default 300, `0` disables).
- The cache key contains a dictionary version stamp built from row counts and
  the newest `updated_at` of entries, meanings and translations. Saving or
  deleting any of those rows invalidates the stamp through signals, so an
  administrative update is visible on the next request instead of after the
  cache window.
- `REDIS_URL` switches the cache to Redis (requires the `redis` package);
  without it the process-local cache is used.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DICTIONARY_SEARCH_DEFAULT_PAGE_SIZE` | `20` | default page size for `/dictionary/search/` |
| `DICTIONARY_SEARCH_MAX_PAGE_SIZE` | `100` | hard cap for `pageSize` |
| `DICTIONARY_SEARCH_CACHE_TTL` | `300` | seconds a rendered search response stays cached |
| `DICTIONARY_VERSION_CACHE_TTL` | `5` | seconds the version stamp stays cached |
| `DICTIONARY_SEARCH_PARTIAL_ROWS` | `200` | cap on rows the substring fallback may add |
| `DICTIONARY_SEARCH_TRANSLATIONS_PER_RESULT` | `6` | translations serialized per hit |
| `SQLITE_BUSY_TIMEOUT` | `20` | seconds SQLite waits for a write lock |

### SQLite settings

The SQLite connection enables `journal_mode=WAL`, `synchronous=NORMAL`,
`temp_store=MEMORY`, a 64 MB page cache, a 256 MB mmap window, a busy timeout
and `IMMEDIATE` transactions (so a read-then-write transaction cannot deadlock
against a concurrent writer). Foreign key enforcement stays on. These settings
are applied per connection through `init_command` and do not modify data.

### Schema changes in this release

| Migration | Change | Reversible |
| --- | --- | --- |
| `learning.0007_translation_updated_at` | adds `Translation.updated_at` and the `(entry_id, target_language)` index, used for cache invalidation and language filtering | yes |
| `learning.0008_dictionary_search_index` | creates the FTS5 table, its triggers and populates the index; skipped automatically when FTS5 or SQLite is unavailable | yes (drops table and triggers) |

Both are additive: no existing row, column or index is dropped.

```powershell
# 1. back up first
Copy-Item backend\db.sqlite3 "backend\db.sqlite3.backup-$(Get-Date -Format yyyyMMdd-HHmmss)"
# 2. apply
backend\.venv\Scripts\python.exe backend\manage.py migrate
# 3. verify
backend\.venv\Scripts\python.exe backend\manage.py rebuild_search_index --check
backend\.venv\Scripts\python.exe backend\manage.py check_dictionary_integrity
```

Rolling back the search index alone:
`backend\.venv\Scripts\python.exe backend\manage.py migrate learning 0007`.

### Tests

```powershell
backend\.venv\Scripts\python.exe backend\manage.py test
```

`learning/test_search.py` covers ranking, language filters, pagination
boundaries, the trigger-driven index synchronisation, ETag/`304` behaviour,
the query budget, and the ORM fallback.

Catalog records are managed through Django admin or imported using the
`import_catalog` management command. The seed JSON mirrors the app's bundled
offline learning corpus; user learning data is never part of that fixture.

## Android architecture

All navigation screens receive a lifecycle-aware `AppViewModel`, not data
repositories. The presentation layer exposes Room-backed `StateFlow` state and
routes user actions through domain use cases and repository contracts. Data
implementations own Room, DataStore, file import, and Retrofit access. Room
remains authoritative for local learning data; catalog refresh and account sync
are additive, so core study flows remain available offline.

### Dictionary search screen

Search is local (Room), so it does not depend on the backend being reachable.

- `AppViewModel.searchState` exposes one immutable `DictionarySearchUiState`
  (`filters`, `isLoading`, `results`, `hasSearched`, `errorMessage`).
- Filter changes are debounced (300 ms), de-duplicated and fed into
  `flatMapLatest`, so a new keystroke cancels the previous query instead of
  queueing it.
- The screen no longer runs the debounce inside `LaunchedEffect`; it only
  forwards text and chip changes, which keeps state across configuration
  changes and removes search work from the composable.
- Loading, empty, error and success states are rendered from that state; the
  error state offers a retry that re-runs the same query.
- Search history is written on explicit submit (keyboard search action) and
  skips a repeat of the last query, instead of recording every debounced
  keystroke.
- The "word of the day" lookup no longer sorts the whole table
  (`ORDER BY RANDOM()`); it picks a random id and reads the first row at or
  after it.

These changes are compile-verified by the project's Gradle build/Android
Studio; the sandbox used for this pass had no Android SDK, so only the backend
test suite was executed here.

## Android development URL

The Android emulator reaches a backend on the host machine at
`http://10.0.2.2:8000/`. A physical device must use the host computer's LAN IP.
Use HTTPS and a production host for release builds.

## Dictionary extraction and translation

The `translation` app turns uploaded documents into text, translates text into
Russian and Uzbek through the OpenAI chat-completions API, and fills the
missing `primary_meaning_ru` / `primary_meaning_uz` fields of the catalog.

All endpoints require `Authorization: Token <token>`. Backfill and job
endpoints additionally require a staff account
(`TRANSLATION_BACKFILL_REQUIRES_STAFF=false` disables that check).

| Variable | Default | Purpose |
| --- | --- | --- |
| `OPENAI_API_KEY` | empty | Enables translation. Without it the endpoints return `503`. |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Any OpenAI-compatible gateway. |
| `OPENAI_MODEL` | `gpt-4o-mini` | Model used for translation. |
| `TRANSLATION_MAX_UPLOAD_BYTES` | `10485760` | Upload size limit. |
| `TRANSLATION_MAX_TEXT_CHARS` | `20000` | Limit for `/translation/text/`. |
| `TRANSLATION_MAX_SEGMENT_CHARS` | `2000` | Chunk size sent to the model. |
| `TRANSLATION_MAX_SEGMENTS` | `40` | Maximum chunks per request. |
| `TRANSLATION_BATCH_SIZE` | `6` | Items per model call. |
| `TRANSLATION_CONCURRENCY` | `1` | Parallel model calls; raise it on a billed key. |
| `DEEPSEEK_API_KEY` | empty | Optional second provider; runs in parallel with the first. |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com/v1` | DeepSeek endpoint. |
| `DEEPSEEK_MODEL` | `deepseek-chat` | DeepSeek model. |

When two providers are configured the client round-robins between them, so both
quotas are used and a rate-limited provider fails over to the other. Check
`GET /api/v1/translation/status/` to see the active providers.

### Seed the 100 000 word English lexicon

```powershell
backend\.venv\Scripts\python.exe -m nltk.downloader wordnet omw-1.4
backend\.venv\Scripts\python.exe backend\manage.py import_wordnet --limit 100000 --translation-language none
```

`import_wordnet` stores headwords, WordNet definitions (`WordMeaning`),
CEFR estimates and frequency scores. Pass `--translation-language rus` when the
Open Multilingual WordNet data for Russian is available; otherwise leave it as
`none` and let `/translation/backfill/` (or the CLI below) fill the Russian and
Uzbek fields with the OpenAI model.

Other sources:

```powershell
# Wiktionary dump from kaikki.org (JSONL, may be gzipped)
backend\.venv\Scripts\python.exe backend\manage.py import_kaikki path\to\kaikki.org-dictionary-English.jsonl.gz

# Ready-made CSV/TSV/JSON/JSONL/word list with flexible column names
backend\.venv\Scripts\python.exe backend\manage.py import_lexicon path\to\words.csv
```

### Professional dictionary data (transcription, POS, article, grammar)

Both paths below write the fields a printed learner's dictionary shows:

| Field | Where | Example |
| --- | --- | --- |
| `phonetic_ipa` | entry | `/ˈwɪmzɪkəl/` |
| `part_of_speech` | entry | `noun`, `verb`, `adjective`, `numeral`, `article` |
| `article` | entry | `a` / `an` (English countable nouns) |
| `plural_form` | entry | `dictionaries` |
| `comparative_form` / `superlative_form` | entry | `more whimsical` / `most whimsical` |
| `translation_text` | translation | `словарь` |
| `part_of_speech`, `gender`, `number`, `article` | translation | `noun`, `m`, `singular`, `-` |
| `context_note` | translation | `сущ., м.р.` (shown next to the word in the app) |
| `quality` | translation | `wiktionary` / `machine` / `reviewed` |

**From Wiktionary (free, no API key):**

```powershell
# 534 MB dump; ~1.4M lines. Safe to re-run: existing values are kept unless --overwrite.
curl.exe -L -o "$env:TEMP\en.jsonl.gz" https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl.gz
backend\.venv\Scripts\python.exe backend\manage.py enrich_from_wiktionary "$env:TEMP\en.jsonl.gz"
```

Wiktionary covers Russian and Uzbek translations, IPA, plural/comparative forms and
gender tags for the words it documents.

**From the model (needs `OPENAI_API_KEY`, fills the remaining words):**

```powershell
backend\.venv\Scripts\python.exe backend\manage.py translate_dictionary --languages ru,uz --professional --limit 0
```

`--professional` asks the model for `pos`, `ipa`, `article`, `plural` and per-language
`text`/`pos`/`gender`, so entries written this way carry the same grammar fields. The
command is resumable with `--start-id` and supports `--dry-run`.

### Translate the catalog

```powershell
$env:OPENAI_API_KEY = "sk-..."
backend\.venv\Scripts\python.exe backend\manage.py translate_dictionary --languages ru,uz --limit 0
```

The command is resumable: stop it at any moment and continue with
`--start-id <last id>`; `--dry-run` previews without writing.

For long runs prefer the queue, so the work survives restarts:

```powershell
# 1. create a job (staff token)
curl -H "Authorization: Token <token>" -H "Content-Type: application/json" `
  -d '{"languages":["ru","uz"],"limit":0}' http://127.0.0.1:8000/api/v1/translation/jobs/

# 2. run the worker (use --watch to keep processing new jobs)
backend\.venv\Scripts\python.exe backend\manage.py run_translation_jobs --watch
```

Job progress is available from `GET /api/v1/translation/jobs/<id>/`
(`processed`, `translated`, `failed`, `progress`, `lastError`).

## Offline dictionary sync

`GET /api/v1/catalog/` returns the small learning corpus (topics,
conversations, grammar, quizzes) plus `dictionary` metadata. The dictionary
itself is streamed page by page:

```
GET /api/v1/dictionary/sync/?afterId=0&limit=500
{
  "entries": [...],
  "meanings": [...],
  "translations": [...],
  "examples": [...],
  "synonyms": [...],
  "forms": [...],
  "collocations": [...],
  "nextAfterId": 800,
  "hasMore": true,
  "serverTime": "2026-10-25T09:00:00+00:00",
  "dictionary": {"entries": 130777, "maxEntryId": 130790, "missingRussian": 0, "missingUzbek": 0}
}
```

- The first page returns `dictionary` metadata; continue while `hasMore` is
  true, passing the previous `nextAfterId`.
- `limit` is capped at 500 (a page is roughly 0.4 MB).
- The endpoint has its own throttle scope (`dictionary_sync`, 600/minute), so a
  full first sync of the 130k corpus finishes in a couple of minutes instead of
  being limited to the shared anonymous rate.
- Incremental updates: pass `since=<serverTime>` from the previous complete
  run. Only entries whose row or any of their meanings changed after that
  timestamp are returned. Rows carry `updatedAt`, which `auto_now` bumps when
  `translate_dictionary` (or admin editing) saves a change.
- Clients should store `nextAfterId` after every page so an interrupted first
  sync resumes instead of restarting.

The Android client (`CloudRepositoryImpl`) does exactly this: one transaction
per page, cursor persisted in `SyncStateStore`, and a progress callback feeding
the cloud sync screen.
