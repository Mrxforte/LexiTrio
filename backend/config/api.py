"""Cross-cutting API plumbing: error mapping and request correlation."""

import contextvars
import logging
import re
import uuid

from django.db import DatabaseError
from django.urls import reverse
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework.views import exception_handler as drf_exception_handler

logger = logging.getLogger(__name__)

_request_id = contextvars.ContextVar("request_id", default="-")
_VALID_REQUEST_ID = re.compile(r"^[A-Za-z0-9._-]{8,64}$")


def api_exception_handler(exc, context):
    """DRF's handling plus a safe answer for database failures.

    Validation and permission errors keep DRF's formats unchanged. A database
    error is logged with its details and reported to the client as a generic
    503, so no SQL, table names or file paths ever reach the response.
    """
    response = drf_exception_handler(exc, context)
    if response is not None:
        return response
    if isinstance(exc, DatabaseError):
        view = context.get("view")
        logger.exception("database error in %s", type(view).__name__)
        return Response({"detail": "Xizmat vaqtincha mavjud emas."}, status=503)
    return None


class RequestIdMiddleware:
    """Gives every request an id (client-supplied if well formed), echoes it in
    ``X-Request-ID`` and exposes it to log records through :class:`RequestIdFilter`."""

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        supplied = request.headers.get("X-Request-ID", "")
        request_id = supplied if _VALID_REQUEST_ID.match(supplied) else uuid.uuid4().hex
        request.request_id = request_id
        token = _request_id.set(request_id)
        try:
            response = self.get_response(request)
        finally:
            _request_id.reset(token)
        response["X-Request-ID"] = request_id
        return response


class RequestIdFilter(logging.Filter):
    def filter(self, record):
        record.request_id = _request_id.get()
        return True


class ApiRootView(APIView):
    """Authenticated index of the API's top-level endpoints (replaces DRF's generated root)."""

    def get(self, request):
        names = [
            "health", "catalog", "dictionary-search", "dictionary-sync", "dictionary-details",
            "content-inventory", "sync",
        ]
        return Response({name: request.build_absolute_uri(reverse(name)) for name in names})
