"""Pagination defaults for the read-only catalog endpoints."""

from rest_framework.pagination import PageNumberPagination


class CatalogPagination(PageNumberPagination):
    """Paged envelope for catalog lists.

    The English corpus alone holds more than 130k entries, so an unbounded
    list response would serialize the whole dictionary. ``pageSize`` lets a
    client ask for a larger page without ever exceeding ``max_page_size``.
    """

    page_size = 50
    page_size_query_param = "pageSize"
    max_page_size = 500
