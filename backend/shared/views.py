"""Base view classes shared by the public catalog apps."""

from rest_framework import filters, permissions, viewsets


class PublicReadOnlyViewSet(viewsets.ReadOnlyModelViewSet):
    """Anonymous, read-only, id-ordered (and therefore stably paged) list/detail endpoints."""

    permission_classes = [permissions.AllowAny]
    filter_backends = [filters.OrderingFilter]
    ordering_fields = ["id"]
    ordering = ["id"]
