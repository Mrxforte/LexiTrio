# LexiTrio Architecture Baseline

## 1. System Overview

LexiTrio is an enterprise trilingual dictionary platform (English, Uzbek, Russian) featuring offline-first mobile capabilities, full-text and ranked dictionary lookup, learning progress tracking, spaced repetition vocabulary, and cloud synchronization.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Android Client Application                      │
│                                                                         │
│  [Compose UI] ──► [AppViewModel / StateFlow] ──► [Use Cases / Domain]     │
│                             │                            │              │
│                             ▼                            ▼              │
│                  [Room Database / DAOs]       [CloudRepository / Api]   │
└─────────────────────────────┬────────────────────────────┬──────────────┘
                              │                            │
                              │ (Offline Sync / REST API)  │
                              ▼                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Django Backend Server                          │
│                                                                         │
│  [Nginx / Proxy] ──► [Gunicorn WSGI / ASGI] ──► [Django REST Framework]   │
│                                                          │              │
│                                       ┌──────────────────┴───────────┐  │
│                                       ▼                              ▼  │
│                              [PostgreSQL / SQLite]           [Redis]    │
│                              (FTS5 / Trigram Index)          (Cache)    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Core Components

### 2.1 Android Application (`/app`)
- **Language & UI Framework**: Kotlin 2.1, Jetpack Compose with Material 3.
- **Local Persistence**: Room DB v3 (`LexiTrioDatabase`) storing ~130k preloaded dictionary entries, translations, meanings, user saved words, quiz attempts, and sync tombstones.
- **Network Stack**: Retrofit 2 + OkHttp 4 + Moshi for JSON parsing.
- **State Management**: MVVM architecture using `AppViewModel`, `StateFlow`, `collectAsState`, and Kotlin Coroutines.
- **Background Work**: `WorkManager` for asynchronous dictionary and progress sync.

### 2.2 Backend Platform (`/backend`)
- **Framework**: Django 5.x + Django REST Framework (DRF).
- **Database Engine**:
  - Development & Embedded: SQLite with WAL mode (`db.sqlite3` holding 130,777 entries and 324,024 translations).
  - Production: PostgreSQL with `psycopg` connection pooling.
- **Search Engine**:
  - SQLite: FTS5 virtual table (`dictionary_search`) synced via triggers.
  - PostgreSQL: Trigram & Full-Text Search fallback (`apps/dictionary/search/engine.py`).
- **Caching**: Local memory / Redis (`django.core.cache.backends.redis.RedisCache`).
- **Data Apps**:
  - `accounts`: Authentication & Token management.
  - `learning`: Catalog, dictionary search, topics, conversations, grammar, and quiz logic.
  - `syncing`: Delta synchronization for saved words, collections, progress, and preferences.
  - `translation`: Third-party AI & provider enrichment pipelines.

---

## 3. Communication Protocols & API Contracts

- **Protocol**: HTTP/1.1 & HTTP/2 REST APIs with JSON payloads.
- **Authentication**: Bearer / DRF Token Authentication.
- **Primary Endpoints**:
  - `GET /api/v1/dictionary/search/?q={query}&source={lang}&target={target}&page={p}&pageSize={n}` - Ranked dictionary search with ETag support.
  - `GET /api/v1/dictionary/sync/?afterId={id}&limit={limit}&since={iso}` - Resumable dictionary catalog export.
  - `GET /api/v1/catalog/` - Catalog metadata snapshot.
  - `POST /api/v1/sync/` - Bidirectional user data sync.

---

## 4. Key Bottlenecks & Risk Areas
1. **ORM Search Fallback**: Plain ORM queries without FTS indexes cost ~800–1000ms per request.
2. **Main Thread Safety**: Ensure large Room database queries in Android use `Dispatchers.IO` and bounded page limits.
3. **Database Concurrency**: PostgreSQL connection pooling and connection limits under high load.
4. **Cache Invalidation**: Keeping Redis cache consistent during dictionary catalog updates.
