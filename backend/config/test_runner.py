"""Isolate shared cache state while keeping throttling active within each test."""
import unittest

from django.core.cache import caches
from django.test.runner import DiscoverRunner


class IsolatedCacheResult(unittest.TextTestResult):
    def startTest(self, test):
        for cache in caches.all():
            cache.clear()
        super().startTest(test)


class IsolatedCacheRunner(unittest.TextTestRunner):
    resultclass = IsolatedCacheResult


class BackendTestRunner(DiscoverRunner):
    test_runner = IsolatedCacheRunner
