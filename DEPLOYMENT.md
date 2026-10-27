# Enterprise Deployment & Infrastructure Guide

This guide details the production deployment, scaling, database migration, backup, and load testing procedures for the LexiTrio Django backend.

---

## 1. System Architecture Overview

```
                        [ Android Client / Web ]
                                   │
                                   ▼
                            [ Nginx Proxy ]
                      (Port 80/443, SSL, Rate Limit)
                                   │
               ┌───────────────────┴───────────────────┐
               ▼                                       ▼
    [ Django App Replica 1 ]                [ Django App Replica 2 ]
     (Gunicorn WSGI Worker)                  (Gunicorn WSGI Worker)
               │                                       │
               ├───────────────────┬───────────────────┤
               ▼                   ▼                   ▼
    [ PostgreSQL 16 DB ]     [ Redis 7 Cache ]   [ Celery Worker ]
   (130k+ Corpus + trgm)   (Session / Search)   (Async Backfills)
```

### Core Services
- **Django Application Server**: Gunicorn WSGI running on Python 3.12 with non-root security.
- **PostgreSQL 16**: Primary dictionary and user database with `pg_trgm` trigram indexes.
- **Redis 7**: Distributed cache, rate limiter, and Celery broker.
- **Nginx 1.27**: Reverse proxy with static asset caching, rate limiting (30r/s), and SSL termination.
- **Celery Worker**: Asynchronous background job worker for bulk translation and index rebuilding.

---

## 2. Quickstart Deployment with Docker Compose

### Prerequisites
- Docker Engine 24+ and Docker Compose 2.20+ installed.

### Steps

1. **Configure Environment Variables**:
   Copy the template and set production secrets:
   ```bash
   cp backend/.env.example .env
   ```
   Ensure `DJANGO_SECRET_KEY` and `POSTGRES_PASSWORD` are changed to secure random values.

2. **Start Infrastructure Stack**:
   ```bash
   docker-compose up -d --build
   ```

3. **Verify Service Health**:
   ```bash
   docker-compose ps
   curl http://localhost/health/
   ```
   Expected response:
   ```json
   {
     "status": "healthy",
     "timestamp": "2026-03-30T12:00:00Z",
     "services": {
       "database": { "status": "ok", "latency_ms": 1.2, "engine": "postgresql" },
       "cache": { "status": "ok", "latency_ms": 0.8, "backend": "RedisCache" }
     }
   }
   ```

---

## 3. Database Migration (SQLite to PostgreSQL)

To migrate the 130,000+ dictionary records from local `db.sqlite3` into production PostgreSQL:

```bash
docker-compose exec backend python manage.py migrate_sqlite_to_postgres --sqlite-path /app/db.sqlite3
```

### Verification & Performance Test
Run dictionary integrity and latency benchmarks inside the container:
```bash
docker-compose exec backend python manage.py check_dictionary_integrity --strict
docker-compose exec backend python manage.py benchmark_dictionary
```

---

## 4. Scaling Application Replicas

To scale Django application workers under heavy load:

1. Update `nginx/conf.d/default.conf` upstream block:
   ```nginx
   upstream django_app {
       server backend:8000 max_fails=3 fail_timeout=10s;
       server backend_2:8000 max_fails=3 fail_timeout=10s;
       keepalive 32;
   }
   ```

2. Launch additional app replica:
   ```bash
   docker-compose up -d --scale backend=2
   ```

---

## 5. Automated Backups & Restoration

### Backup
Run timestamped, gzip-compressed database backups with SHA-256 integrity verification:
```bash
python scripts/backup_db.py --output-dir backups/
```
Output files:
- `backups/lexitrio_postgres_backup_YYYYMMDD_HHMMSS.sql.gz`
- `backups/lexitrio_postgres_backup_YYYYMMDD_HHMMSS.sha256`

### Restore
To restore from a backup file:
```bash
python scripts/restore_db.py backups/lexitrio_postgres_backup_YYYYMMDD_HHMMSS.sql.gz
```

---

## 6. Load Testing

Run Locust load tests to measure throughput and p95/p99 latency under concurrent traffic:

```bash
pip install locust
locust -f backend/load_testing/locustfile.py --host=http://localhost
```

Or run headless load test with 50 concurrent users spawning at 10 users/sec for 1 minute:
```bash
locust -f backend/load_testing/locustfile.py --host=http://localhost --headless -u 50 -r 10 --run-time 1m
```
