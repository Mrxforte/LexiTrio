# Backend architecture

One Django project, **one Django app per feature**. Every app follows the standard Django layout (`apps.py`, `models.py`, `admin.py`, `views.py`, `serializers.py`, `urls.py`, `tests.py`, `migrations/`), and apps that belong together are grouped in a folder.

```
backend/
├── config/                 settings, root urls, celery, error handling (api.py)
├── shared/                 helpers used by several apps: pagination, serializer base, text, http, migration_utils
├── apps/
│   ├── accounts/           registration and token login
│   ├── dictionary/
│   │   ├── entries/        label "dictionary"      models: DictionaryEntry, WordMeaning, Translation, ...
│   │   ├── search/         label "dictionary_search"  FTS5 / PostgreSQL / ORM engines, /dictionary/search/, index commands
│   │   └── sync/           label "dictionary_sync"    delta sync, deletions, batched details
│   ├── catalog/
│   │   ├── topics/         Topic, TopicWord
│   │   ├── conversations/  Conversation, ConversationTurn
│   │   ├── grammar/        GrammarLesson
│   │   ├── quiz/           QuizQuestion
│   │   └── snapshot/       /catalog/ snapshot view, import_catalog
│   ├── curriculum/
│   │   ├── content/        label "curriculum"   courses, levels, lessons, exercises, test bank, batches, inventory
│   │   └── progress/       label "curriculum_progress"   UserTopicProgress, MistakeRecord
│   ├── userdata/           saved words, collections, quiz attempts, progress, preferences
│   ├── syncing/            POST/GET /sync/ for userdata (last-writer-wins merge)
│   ├── translation/        machine translation, imports, translation jobs
│   ├── maintenance/        cross-app operations (SQLite -> PostgreSQL copy)
│   └── learning/           legacy schema history only: migrations, no code
└── tests/                  API contract and architecture guards
```

URLs, JSON fields, status codes and app labels of the public API did not change. `tests/test_api_contract.py` compares every endpoint with `tests/contracts/api_shapes.json`, recorded before the split.

## Inside an app

| File | Responsibility |
| --- | --- |
| `models.py`, `choices.py` | persistence and the stored values (enums) of that feature |
| `serializers.py` | JSON representation (camelCase through `shared.serializers`) |
| `views.py`, `urls.py` | HTTP only: parse, validate, call a service, map the result |
| `services.py` / `services/` | use cases without `rest_framework`/`django.http` imports |
| `admin.py`, `signals.py`, `management/commands/` | admin, side effects that must run on every write path, CLI |
| `tests.py` | the feature's tests |

`tests/test_architecture.py` fails if a service or choices module imports the API layer, if a feature app is missing `apps.py`, or if `dictionary.entries` starts depending on `search`/`sync` (dependencies point one way: search and sync use entries).

## Dependencies between apps

```
entries  <-  search, sync, topics (word links), translation, snapshot
curriculum.content  <-  curriculum.progress
topics, conversations, grammar, quiz, entries(sync)  <-  snapshot
userdata  <-  syncing
```

`/dictionary/entries/` is registered by the **search** app (a `?q=` on it is answered by the ranked index); the other dictionary tables are browsed through `entries`.

## The database did not move

The former `learning` app owned all these tables. They keep their names (`Meta.db_table = "learning_..."`), so no data was copied or renamed. Each new app's `0001_initial` only *adopts* its tables: it changes Django's migration state, runs no SQL, and re-labels the existing content types so permissions and admin history follow the models (`shared/migration_utils.py`). `apps/learning/0013` removes the models from the legacy state. On a fresh database `apps/learning/0001..0012` still create the tables first.

Verified on a copy and then on the real database: identical schema fingerprint (all 134 objects) and identical row counts before and after; `makemigrations --check` reports no changes.

If you ever want `dictionary_*` table names, do it later with a deliberate `AlterModelTable` migration; nothing depends on the prefix except raw SQL in `search/engine.py` and `search/fts.py`.

## Stored values (enums)

Each feature owns its `choices.py`; values are persisted and used by the client and never change.
`PublicationStatus`, `ValidationStatus`, `ExerciseType`, `GrammarTableType`, `ContentBatchStatus` (curriculum.content) · `TopicProgressStatus` (curriculum.progress) · `TranslationQuality` (dictionary.entries) · `TranslationJobStatus` (translation; a job *runs*, a batch is *in progress*, so they stay separate). Models keep the old nested names as aliases (`LessonContent.Status is PublicationStatus`).

## Cross-cutting

* **Errors:** DRF formats are unchanged; a database error becomes a generic `503 {"detail": ...}`, details only in the log (`config/api.py`).
* **Logging:** every record carries a request id; `X-Request-ID` is honoured when well formed, otherwise generated.
* **Normalisation:** `shared.text.normalize_text` is the one definition of "the same word" for search, imports and generated entries.
* **Signals:** `dictionary.entries.signals` invalidates the dictionary version cache and records deletion tombstones; both must happen on every write path.

## Deliberately not done

* No `domain/entities` or repository interfaces: the apps are CRUD over Django models with one real engine.
* `settings.py` is not split into base/development/production: it is driven by `DJANGO_DEBUG` and `config.settings` is the path Docker, gunicorn, Celery and `manage.py` use.
* `import_curriculum.py` still embeds seed content in code (480 lines); moving it to data files is the next candidate.
