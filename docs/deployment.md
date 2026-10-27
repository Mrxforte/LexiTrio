# LexiTrio Deployment Guide

## 1. Prerequisites
- Docker & Docker Compose v2+
- PostgreSQL 16+ (or managed RDS/Cloud SQL)
- Redis 7+ (or ElastiCache/Upstash)

---

## 2. Environment Variables Configuration

Template `.env` file for backend production:

```env
DJANGO_DEBUG=false
DJANGO_SECRET_KEY=generate_a_secure_random_key_50_chars
DJANGO_ALLOWED_HOSTS=api.lexitrio.com,localhost
DATABASE_URL=postgres://lexitrio_user:secure_password@db:5432/lexitrio
REDIS_URL=redis://redis:6379/0
DICTIONARY_SEARCH_CACHE_TTL=300
DICTIONARY_VERSION_CACHE_TTL=5
```

---

## 3. Deployment Steps via Docker Compose

```bash
# 1. Build and start services
docker-compose up -d --build

# 2. Run migrations
docker-compose exec backend python manage.py migrate

# 3. Check dictionary database integrity
docker-compose exec backend python manage.py check_dictionary_integrity

# 4. Check backend logs
docker-compose logs -f backend
```

---

## 4. Reverse Proxy (Nginx) Sample Configuration

```nginx
server {
    listen 443 ssl http2;
    server_name api.lexitrio.com;

    ssl_certificate /etc/letsencrypt/live/api.lexitrio.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.lexitrio.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```
