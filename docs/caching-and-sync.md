# Caching and synchronisation

How the Android app keeps the dictionary (≈423k words) fast, offline-capable and in sync with the Django backend.

## Data flow

| Concern | Mechanism | Code |
| --- | --- | --- |
| Search | Local-first: Room index range scan (headwords, alphabetical, paged) + FTS4 (meanings). The server is asked only while a large download pass is running, and only fills the tail of page 1. | `DictionaryRepository`, `DictionaryDao` |
| Core dictionary | Background "lite" download (entries + meanings), resumable cursor, 8-minute worker budget. Later passes fetch only changes (`since`). | `DictionaryDownloader`, `DictionaryDownloadScheduler` |
| Lazy content | Translations, examples, forms, collocations and synonyms are fetched per word on first open (one batched request for up to 50 ids) and stored in Room. | `DetailCacheManager`, `GET /dictionary/details/` |
| Adaptive cache | `cache_metadata` (priority, last access, access count, last validation, server version). Score = `w_f·ln(1+accesses) + w_r·0.5^(age/half-life)`. | `CachePolicy`, `CacheDao` |
| Prefetch / eviction | Daily unique job on unmetered network with spare battery and storage: at most 2 requests of 50 words, then eviction down to the budget. | `AdaptivePrefetchWorker` |
| HTTP | OkHttp disk cache (20 MB) honours `ETag` / `Cache-Control`; weak ETags (added by gzip) match. | `CloudApiFactory`, `views._etag_matches` |
| User data | Saved words, collections, attempts, preferences sync through `POST /sync/` (login required); tombstones and client UUIDs make retries idempotent. | `SyncWorker`, `CloudRepositoryImpl` |

Foreground searches never wait for WorkManager.

## Cache priorities

| Class | Content | Eviction |
| --- | --- | --- |
| P0 | Saved words (favourites and the rest of the user's list) | never |
| P1 | Words of bundled topics (project data) | never |
| P2 | Words the user opened | by score, only detail content |
| P3 | Everything else | not downloaded unless requested |

Word rows and meanings are the offline index and are **never** evicted; eviction removes only lazily loaded detail rows of unprotected words, in one transaction per chunk, and resets `detailValidatedAt` so the word is simply a cache miss next time. Protection is re-checked in SQL against `saved_words` / `topic_word_cross_ref`, not only through the stored priority.

## TTL and revalidation

| Content | Policy |
| --- | --- |
| Word index (entries, meanings) | Delta sync every 12 h (periodic) and on each launch; no TTL |
| Detail content | 7 days (`CachePolicy.detailTtlMs`); revalidated when the word is opened or prefetched |
| Search responses (HTTP) | `max-age` 5 s, then `If-None-Match`; server result cache 5 min keyed by dictionary version |
| In-memory search results | LRU of 200 pages, cleared whenever background writes bump `SearchCacheVersion` |

## Synchronisation rules

* **Cursor:** `since` = server time at the *start* of the previous completed pass, stored with the unfinished pass so an interrupted pass resumes with its original watermark. The cursor (`afterId`) is saved only after the page's Room transaction committed.
* **Deletions:** deleting a `DictionaryEntry` writes a `DictionaryTombstone`; the first page of an incremental pass carries `deletedIds` (cap 5000, `deletedIdsTruncated`). The client deletes them in the same transaction as the page, except words the user saved.
* **Invalid / too old cursor:** older than `DICTIONARY_TOMBSTONE_RETENTION_DAYS` (365) the server ignores `since`, sends everything and marks `fullResync`. Known limit: rows deleted during that gap stay in the local copy until the word is revalidated (`missingIds` in the details response removes them).
* **Conflicts:** dictionary content is server-authoritative. User data (saved words, favourites, progress) is last-writer-wins on `updatedAt` through the existing `/sync/` endpoint; a server-side deletion never removes a saved word locally.
* **Failures:** I/O errors and HTTP 429/5xx retry; 4xx fail the job. Worker budget pauses use a short linear backoff, prefetch uses exponential backoff (1 h).

## Backend API

* `GET /api/v1/dictionary/search/` ranked, paged, ETag.
* `GET /api/v1/dictionary/sync/?afterId&limit&since&lite=1` paged delta (lite: ≤2000 rows, entries + meanings only; otherwise ≤500 with children).
* `GET /api/v1/dictionary/details/?ids=1,2,…` ≤50 ids, constant number of queries, `missingIds`.
* All are public and read-only, rate limited (`dictionary_search`, `dictionary_sync`), validated (400 with field errors).

Configuration: `DICTIONARY_*` environment variables in `config/settings.py`; client limits in `CachePolicy`.

## Measurements

Real numbers from this repository; desktop/JVM figures are **not** device timings.

| Measurement | Result |
| --- | --- |
| Old local search (`LIKE '%q%'` over 423k rows, desktop SQLite) | 167–318 ms per query |
| New headword query (index range, same data) | 0.04–0.26 ms |
| FTS4 meaning query, same data | 0.4–1.5 ms |
| 50k-row Room database, Robolectric JVM (`AdaptiveCacheTest`) | insert 6.9 s, cold search 21 ms, warm (LRU) 0 ms, page at offset 20 000: 5 ms, meaning match 9 ms |
| Backend search API, 423k rows (Django test client) | 64–121 ms cold, 0.7–1.9 ms warm (cached), 2–5 KB gzip |
| Lite sync page (2000 entries) | ≈350 KB gzip, 0.3 s server time |
| Full lite download on the emulator | ≈10 min, resumable; no user wait because search works meanwhile |

Re-run the backend figures with `python manage.py benchmark_dictionary`; the JVM benchmark prints a `BENCH` line from `AdaptiveCacheTest.searchStaysFastOnALargeDictionary`.

## Not implemented / manual verification

* Android Macrobenchmark (no benchmark module in the build) and on-device memory/disk profiling.
* Pronunciation audio cache (TTS is generated on device in this app).
* Search-history upload and other telemetry (deliberately off: privacy).
* Not covered by automated tests: worker uniqueness (needs `work-testing`; unique names with `KEEP` are used), the UI scroll-to-load-more path, behaviour on slow real networks.
