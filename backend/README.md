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
- `GET /api/v1/dictionary/entries/?q=...&language=en`
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
- `limit` is capped at 500 (a page is roughly 0.5 MB).
- Incremental updates: pass `since=<serverTime>` from the previous complete
  run. Only entries whose row or any of their meanings changed after that
  timestamp are returned. Rows carry `updatedAt`, which `auto_now` bumps when
  `translate_dictionary` (or admin editing) saves a change.
- Clients should store `nextAfterId` after every page so an interrupted first
  sync resumes instead of restarting.

The Android client (`CloudRepositoryImpl`) does exactly this: one transaction
per page, cursor persisted in `SyncStateStore`, and a progress callback feeding
the cloud sync screen.
