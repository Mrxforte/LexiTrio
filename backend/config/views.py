import time
from django.core.cache import cache
from django.db import connection, DatabaseError
from django.utils import timezone
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView


class HealthCheckView(APIView):
    """Production health & readiness check endpoint.

    Verifies connectivity to primary infrastructure dependencies:
    - PostgreSQL/SQLite Database
    - Redis Cache
    Returns HTTP 200 if all services are operational, 503 if any core component fails.
    """

    permission_classes = [permissions.AllowAny]

    def get(self, request):
        db_healthy = False
        db_error = None
        db_latency_ms = 0.0

        cache_healthy = False
        cache_error = None
        cache_latency_ms = 0.0

        # Probe Database
        try:
            started = time.monotonic()
            with connection.cursor() as cursor:
                cursor.execute("SELECT 1;")
                cursor.fetchone()
            db_latency_ms = round((time.monotonic() - started) * 1000, 2)
            db_healthy = True
        except Exception as exc:
            db_error = str(exc)

        # Probe Cache
        try:
            started = time.monotonic()
            test_key = "health_check_probe"
            cache.set(test_key, "ok", timeout=10)
            probe_val = cache.get(test_key)
            cache_latency_ms = round((time.monotonic() - started) * 1000, 2)
            if probe_val == "ok":
                cache_healthy = True
            else:
                cache_error = "Cache get did not match written value"
        except Exception as exc:
            cache_error = str(exc)

        is_healthy = db_healthy and cache_healthy
        response_status = (
            status.HTTP_200_OK if is_healthy else status.HTTP_503_SERVICE_UNAVAILABLE
        )

        payload = {
            "status": "healthy" if is_healthy else "unhealthy",
            "timestamp": timezone.now().isoformat(),
            "services": {
                "database": {
                    "status": "ok" if db_healthy else "error",
                    "latency_ms": db_latency_ms,
                    "engine": connection.vendor,
                    "error": db_error,
                },
                "cache": {
                    "status": "ok" if cache_healthy else "error",
                    "latency_ms": cache_latency_ms,
                    "backend": cache.__class__.__name__,
                    "error": cache_error,
                },
            },
        }

        return Response(payload, status=response_status)
