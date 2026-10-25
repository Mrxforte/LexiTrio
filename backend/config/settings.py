import os
from pathlib import Path

from django.core.exceptions import ImproperlyConfigured

BASE_DIR = Path(__file__).resolve().parent.parent

try:  # Load backend/.env when python-dotenv is installed.
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover - optional dependency
    load_dotenv = None
if load_dotenv is not None:
    load_dotenv(BASE_DIR / ".env")

DEBUG = os.environ.get("DJANGO_DEBUG", "true").lower() == "true"
SECRET_KEY = os.environ.get("DJANGO_SECRET_KEY")
if not SECRET_KEY and not DEBUG:
    raise ImproperlyConfigured("DJANGO_SECRET_KEY must be set when DEBUG is false.")
SECRET_KEY = SECRET_KEY or "local-development-only-key-do-not-deploy"
ALLOWED_HOSTS = [
    host.strip()
    for host in os.environ.get("DJANGO_ALLOWED_HOSTS", "localhost,127.0.0.1,10.0.2.2").split(",")
    if host.strip()
]

INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "rest_framework",
    "rest_framework.authtoken",
    "accounts",
    "learning",
    "syncing",
    "translation",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"
TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    }
]
WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"

if os.environ.get("DATABASE_URL", "").startswith("postgres"):
    import urllib.parse

    database_url = urllib.parse.urlparse(os.environ["DATABASE_URL"])
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.postgresql",
            "NAME": database_url.path.lstrip("/"),
            "USER": database_url.username or "",
            "PASSWORD": database_url.password or "",
            "HOST": database_url.hostname or "",
            "PORT": database_url.port or 5432,
            "CONN_MAX_AGE": 60,
        }
    }
else:
    DATABASES = {
        "default": {
            "ENGINE": "django.db.backends.sqlite3",
            "NAME": BASE_DIR / "db.sqlite3",
            "OPTIONS": {
                # Wait instead of failing once a writer holds the lock.
                "timeout": float(os.environ.get("SQLITE_BUSY_TIMEOUT", "20")),
                # Take the write lock up front so a read-then-write transaction
                # cannot deadlock against a concurrent writer.
                "transaction_mode": "IMMEDIATE",
                "init_command": (
                    # Readers never block writers and writers never block readers.
                    "PRAGMA journal_mode = WAL;"
                    # WAL + NORMAL survives application crashes and loses no
                    # committed transaction; full fsync per commit is not needed.
                    "PRAGMA synchronous = NORMAL;"
                    "PRAGMA temp_store = MEMORY;"
                    "PRAGMA cache_size = -65536;"
                    "PRAGMA mmap_size = 268435456;"
                ),
            },
        }
    }

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]
LANGUAGE_CODE = "uz"
TIME_ZONE = "UTC"
USE_I18N = True
USE_TZ = True
STATIC_URL = "static/"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": [
        "rest_framework.authentication.TokenAuthentication",
    ],
    "DEFAULT_PERMISSION_CLASSES": [
        "rest_framework.permissions.IsAuthenticated",
    ],
    # Catalog list endpoints are paged: the English corpus alone holds 130k
    # entries, so an unbounded list response is never acceptable.
    "DEFAULT_PAGINATION_CLASS": "learning.pagination.CatalogPagination",
    "PAGE_SIZE": 50,
    "DEFAULT_THROTTLE_CLASSES": [
        "rest_framework.throttling.AnonRateThrottle",
        "rest_framework.throttling.UserRateThrottle",
    ],
    "DEFAULT_THROTTLE_RATES": {
        "anon": "30/minute",
        "user": "120/minute",
        "dictionary_search": "240/minute",
        "translation": "20/minute",
        "translation_admin": "60/minute",
        "dictionary_sync": "600/minute",
    },
}

CACHES = {
    "default": {
        "BACKEND": "django.core.cache.backends.locmem.LocMemCache",
        "LOCATION": "lexitrio",
    }
}
if os.environ.get("REDIS_URL"):
    # Shared cache for multi-process deployments; requires the `redis` package.
    CACHES = {
        "default": {
            "BACKEND": "django.core.cache.backends.redis.RedisCache",
            "LOCATION": os.environ["REDIS_URL"],
        }
    }

DICTIONARY_SEARCH_DEFAULT_PAGE_SIZE = int(
    os.environ.get("DICTIONARY_SEARCH_DEFAULT_PAGE_SIZE", "20")
)
DICTIONARY_SEARCH_MAX_PAGE_SIZE = int(
    os.environ.get("DICTIONARY_SEARCH_MAX_PAGE_SIZE", "100")
)
# Seconds a rendered search response stays cached; 0 disables result caching.
DICTIONARY_SEARCH_CACHE_TTL = int(os.environ.get("DICTIONARY_SEARCH_CACHE_TTL", "300"))
# Seconds the aggregate "dictionary version" stamp stays cached.
DICTIONARY_VERSION_CACHE_TTL = int(os.environ.get("DICTIONARY_VERSION_CACHE_TTL", "5"))
# Maximum number of rows the substring ("partial") scan may contribute per page.
DICTIONARY_SEARCH_PARTIAL_ROWS = int(os.environ.get("DICTIONARY_SEARCH_PARTIAL_ROWS", "200"))
# Rows serialized into each search response translation list.
DICTIONARY_SEARCH_TRANSLATIONS_PER_RESULT = int(
    os.environ.get("DICTIONARY_SEARCH_TRANSLATIONS_PER_RESULT", "6")
)

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")
OPENAI_BASE_URL = os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1")
OPENAI_MODEL = os.environ.get("OPENAI_MODEL", "gpt-4o-mini")
OPENAI_TIMEOUT = float(os.environ.get("OPENAI_TIMEOUT", "90"))
OPENAI_MAX_RETRIES = int(os.environ.get("OPENAI_MAX_RETRIES", "2"))
OPENAI_MAX_BACKOFF = float(os.environ.get("OPENAI_MAX_BACKOFF", "60"))
OPENAI_MIN_INTERVAL = float(os.environ.get("OPENAI_MIN_INTERVAL", "0"))
TRANSLATION_CONCURRENCY = int(os.environ.get("TRANSLATION_CONCURRENCY", "1"))

# Additional OpenAI-compatible providers run in parallel with the first one.
DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
DEEPSEEK_MODEL = os.environ.get("DEEPSEEK_MODEL", "deepseek-chat")
DEEPSEEK_MIN_INTERVAL = float(os.environ.get("DEEPSEEK_MIN_INTERVAL", "0"))
TRANSLATION_PROVIDER_COOLDOWN = float(
    os.environ.get("TRANSLATION_PROVIDER_COOLDOWN", "300")
)
OPENAI_TEMPERATURE = float(os.environ.get("OPENAI_TEMPERATURE", "0.2"))
OPENAI_MAX_OUTPUT_TOKENS = int(os.environ.get("OPENAI_MAX_OUTPUT_TOKENS", "8192"))
TRANSLATION_JSON_MODE = os.environ.get("TRANSLATION_JSON_MODE", "true").lower() == "true"

TRANSLATION_MAX_UPLOAD_BYTES = int(
    os.environ.get("TRANSLATION_MAX_UPLOAD_BYTES", str(10 * 1024 * 1024))
)
TRANSLATION_MAX_TEXT_CHARS = int(os.environ.get("TRANSLATION_MAX_TEXT_CHARS", "20000"))
TRANSLATION_MAX_SEGMENT_CHARS = int(os.environ.get("TRANSLATION_MAX_SEGMENT_CHARS", "2000"))
TRANSLATION_MAX_SEGMENTS = int(os.environ.get("TRANSLATION_MAX_SEGMENTS", "40"))
TRANSLATION_BATCH_SIZE = int(os.environ.get("TRANSLATION_BATCH_SIZE", "6"))
TRANSLATION_BACKFILL_REQUIRES_STAFF = (
    os.environ.get("TRANSLATION_BACKFILL_REQUIRES_STAFF", "true").lower() == "true"
)

SECURE_SSL_REDIRECT = os.environ.get(
    "DJANGO_SECURE_SSL_REDIRECT", "false" if DEBUG else "true"
).lower() == "true"
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
SECURE_HSTS_SECONDS = int(os.environ.get("DJANGO_SECURE_HSTS_SECONDS", "0" if DEBUG else "31536000"))
SECURE_HSTS_INCLUDE_SUBDOMAINS = not DEBUG
SECURE_HSTS_PRELOAD = not DEBUG
SESSION_COOKIE_SECURE = not DEBUG
CSRF_COOKIE_SECURE = not DEBUG
