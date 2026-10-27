# LexiTrio Backup & Recovery Procedures

## 1. Database Backup Strategies

### 1.1 PostgreSQL Production Database
To perform an online, non-blocking backup of the primary PostgreSQL database:

```bash
# Automated daily pg_dump compressed backup
pg_dump -U lexitrio_user -h db -Fc lexitrio > /backups/lexitrio_$(date +%Y%m%d_%H%M%S).dump
```

### 1.2 SQLite File Backup (Development / Embedded)
```bash
# Safe SQLite online backup using SQLite backup API or sqlite3 CLI
sqlite3 backend/db.sqlite3 ".backup '/backups/db_backup_$(date +%Y%m%d).sqlite3'"
```

---

## 2. Disaster Recovery Procedure

1. **Provision Target Environment**: Ensure PostgreSQL and Redis instances are running.
2. **Restore Database Backup**:
   ```bash
   # Restore PostgreSQL database dump
   pg_restore -U lexitrio_user -h db -d lexitrio --clean --if-exists /backups/lexitrio_latest.dump
   ```
3. **Run Migrations & Integrity Checks**:
   ```bash
   python manage.py migrate
   python manage.py check_dictionary_integrity
   ```
4. **Flush Cache**:
   ```bash
   redis-cli -u redis://redis:6379/0 FLUSHALL
   ```
5. **Restart Application Workers**: Verify health endpoints (`GET /api/v1/catalog/`).
