"""Cache invalidation hooks for dictionary content.

The search cache is keyed by a version stamp, and the stamp is cached for a
few seconds to keep bursts of searches cheap. These signals drop the stamp as
soon as a dictionary row is written, so an administrative edit is visible on
the very next request instead of after the cache window.
"""

from django.db.models.signals import post_delete, post_save
from django.dispatch import receiver

from .cache import invalidate_dictionary_cache
from .models import DictionaryEntry, Translation, WordMeaning


@receiver(post_save, sender=DictionaryEntry)
@receiver(post_delete, sender=DictionaryEntry)
@receiver(post_save, sender=Translation)
@receiver(post_delete, sender=Translation)
@receiver(post_save, sender=WordMeaning)
@receiver(post_delete, sender=WordMeaning)
def _invalidate_dictionary_cache(**kwargs):
    invalidate_dictionary_cache()
