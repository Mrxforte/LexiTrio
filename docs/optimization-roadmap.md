# LexiTrio Optimization Roadmap

This document outlines the prioritized action items to transform LexiTrio into an enterprise-ready trilingual dictionary platform.

---

## Phase 1 — Database & Backend Optimizations

1. **PostgreSQL Trigram & FTS Integration**:
   - Ensure PostgreSQL queries in `apps/dictionary/search/engine.py` utilize gin/trgm indexes (`gin_trgm_ops`) on `headword_normalized` and `translation_text`.
   - Prevent N+1 queries using `select_related()` and `prefetch_related()`.
2. **Redis Caching Strategy**:
   - Cache popular dictionary searches and language metadata in Redis.
   - Configure TTL and cache key normalization.
3. **API Security & Throttle Refinement**:
   - Verify CORS, CSRF, rate-limiting, and error-handling middleware.
   - Enforce pagination limits on all catalog/dictionary endpoints.

---

## Phase 2 — Android & Offline Sync Optimizations

1. **Room Query & Index Optimization**:
   - Ensure Room indices on `dictionary_entries` (`headwordNormalized`, `language`, `frequencyRank`) and `translations` (`entryId`, `targetLanguage`).
2. **Search Flow Efficiency**:
   - Verify debouncing (150–300ms) and cancellation of obsolete search requests via `flatMapLatest`.
   - Ensure UI immutability and stable keys in Compose `LazyColumn`.
3. **Resumable Sync Engine**:
   - Optimize `DictionarySyncView` batch pagination and WorkManager sync background retries.

---

## Phase 3 — Verification, Load Testing & Operations

1. **Automated Unit & Integration Tests**:
   - Expand unit tests for dictionary search, pagination, cache invalidation, and auth.
2. **Load Testing with Locust / k6**:
   - Benchmark concurrent backend requests (10, 50, 100, 250, 500 VUs).
3. **Deployment & DevOps Configuration**:
   - Prepare Docker Compose setup, production settings template, backup & restore procedures.
