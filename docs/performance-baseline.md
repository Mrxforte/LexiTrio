# LexiTrio Performance Baseline Report

## 1. Environment Specifications
- **Database Corpus**: 130,777 Dictionary Entries, 324,024 Translations, 130,000+ Meanings.
- **SQLite Database File**: 234 MB (`backend/db.sqlite3`).
- **Python Version**: 3.14 / Django 5.x.
- **Android Platform**: Target SDK 36, Min SDK 24.

---

## 2. Backend Search Performance Benchmarks

Measured using `python manage.py benchmark_dictionary`:

| Test Query | Match Type | Search Backend | Median Latency (ms) | Min Latency (ms) | Result Rows |
|---|---|---|---|---|---|
| `apple` | Exact Word | FTS5 (SQLite) | **2.04 ms** | 1.67 ms | 20 |
| `apple` | Exact Word | ORM Fallback | **907.82 ms** | 817.70 ms | 20 |
| `app` | Prefix | FTS5 (SQLite) | **10.78 ms** | 10.44 ms | 20 |
| `app` | Prefix | ORM Fallback | **850.79 ms** | 744.04 ms | 20 |
| `olma` | Uzbek Translation | FTS5 (SQLite) | **2.41 ms** | 2.00 ms | 20 |
| `olma` | Uzbek Translation | ORM Fallback | **766.23 ms** | 716.40 ms | 20 |
| `яблоко` | Russian Word | FTS5 (SQLite) | **1.06 ms** | 0.94 ms | 20 |
| `яблоко` | Russian Word | ORM Fallback | **780.05 ms** | 523.00 ms | 20 |
| `pple` | Substring | FTS5 (SQLite) | **6.37 ms** | 6.03 ms | 20 |
| `pple` | Substring | ORM Fallback | **1064.04 ms** | 932.13 ms | 20 |
| `look after` | Multi-Word | FTS5 (SQLite) | **29.29 ms** | 27.34 ms | 2 |
| `look after` | Multi-Word | ORM Fallback | **1039.72 ms** | 944.87 ms | 2 |

---

## 3. Analysis & Key Insights

1. **FTS5 Index Efficiency**: SQLite FTS5 yields sub-10ms response times for almost all single-word queries, and ~29ms for multi-word queries.
2. **ORM Fallback Latency**: Standard SQL `LIKE '%query%'` scans take 750–1060ms across 130k rows. In production (PostgreSQL),pg_trgm indexes or PostgreSQL Full-Text Search (`to_tsvector`) must be configured to guarantee sub-50ms latency under load.
3. **Android App Performance**:
   - Debounce interval: 300ms.
   - Search result pagination: Bounded to 50 items per DAO query.
   - Initial local database load time: Preloaded via `PreloadDictionaryData` on first app launch.
