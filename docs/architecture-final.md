# LexiTrio Final Enterprise Architecture

## 1. Production Target Architecture

LexiTrio is structured for horizontal scaling and enterprise reliability.

```
                               ┌────────────────────────────────┐
                               │     Android App (Compose/Room) │
                               └───────────────┬────────────────┘
                                               │ HTTPS REST
                                               ▼
                               ┌────────────────────────────────┐
                               │  Nginx Reverse Proxy / Ingress  │
                               └───────────────┬────────────────┘
                                               │
                      ┌────────────────────────┴────────────────────────┐
                      ▼                                                 ▼
        ┌──────────────────────────┐                      ┌──────────────────────────┐
        │ Gunicorn Application W1 │                      │ Gunicorn Application W2 │
        └─────────────┬────────────┘                      └─────────────┬────────────┘
                      │                                                 │
                      ├────────────────────────┬────────────────────────┤
                      ▼                        ▼                        ▼
           ┌─────────────────────┐   ┌───────────────────┐   ┌────────────────────┐
           │ PostgreSQL Database │   │   Redis Cluster   │   │ OpenTelemetry /    │
           │ (FTS / pg_trgm)     │   │   (Shared Cache)  │   │ Prometheus Metrics │
           └─────────────────────┘   └───────────────────┘   └────────────────────┘
```

---

## 2. Key Architectural Enhancements

### 2.1 Backend & Database
- **Multi-Database Support**: Transparent support for SQLite (development/embedded) and PostgreSQL (production).
- **Optimized Indexes**:
  - `dict_lang_freq_headword_idx` on `(language, -frequency_rank, headword)` for instant filtered pagination.
  - `trans_target_text_idx` on `(target_language, translation_text)` for reverse translations.
  - FTS5 virtual table on SQLite / `pg_trgm` & `tsvector` on PostgreSQL.
- **Cache Strategy**: ETag response headers, 300s TTL search cache, 5s dictionary version key in Redis.
- **API Quality**: Throttle rate limits (`anon: 30/min`, `user: 120/min`, `dictionary_search: 240/min`), ISO 8601 timestamps, structured exception handling.

### 2.2 Android Application
- **Room Persistence**: Preloaded database v3 with composite indices and `withTransaction` batch insertions.
- **Debounced Search**: `AppViewModel` debounces queries at 300ms using `flatMapLatest` to cancel outdated searches.
- **Offline Sync**: Resumable cursor-based sync (`afterId`, `limit`, `since`) managed via `WorkManager` and `SyncWorker`.
- **UI Performance**: Jetpack Compose `LazyColumn` with stable item keys and Material 3 design system.
