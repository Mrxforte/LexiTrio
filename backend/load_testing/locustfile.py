import random
from locust import HttpUser, task, between

POPULAR_SEARCH_TERMS = [
    "apple", "book", "house", "water", "tree",
    "olma", "kitob", "uy", "suv", "daraxt",
    "яблоко", "книга", "дом", "вода", "дерево",
]

LONG_TAIL_TERMS = [
    "education", "knowledge", "university", "student", "teacher",
    "kompyuter", "ta'lim", "lug'at", "maktab", "talaba",
    "компьютер", "образование", "словарь", "школа", "студент",
    "run", "walk", "write", "read", "speak",
]

PREFIX_TERMS = ["ap", "bo", "co", "di", "la", "ol", "ki", "яб", "кн", "об"]


class DictionaryUser(HttpUser):
    """Simulates real Android client activity on LexiTrio Django backend."""

    wait_time = between(0.2, 1.5)

    @task(10)
    def search_popular_word_cached(self):
        """Simulates high-frequency cached searches."""
        word = random.choice(POPULAR_SEARCH_TERMS)
        self.client.get(
            f"/api/v1/dictionary/search/?q={word}",
            name="/api/v1/dictionary/search/?q=[popular_cached]"
        )

    @task(5)
    def search_prefix(self):
        """Simulates search-as-you-type prefix queries."""
        prefix = random.choice(PREFIX_TERMS)
        self.client.get(
            f"/api/v1/dictionary/search/?q={prefix}",
            name="/api/v1/dictionary/search/?q=[prefix]"
        )

    @task(4)
    def search_long_tail(self):
        """Simulates long-tail dictionary lookups."""
        word = random.choice(LONG_TAIL_TERMS)
        self.client.get(
            f"/api/v1/dictionary/search/?q={word}",
            name="/api/v1/dictionary/search/?q=[long_tail]"
        )

    @task(3)
    def search_with_language_and_pos_filter(self):
        """Simulates filtered dictionary searches."""
        word = random.choice(POPULAR_SEARCH_TERMS[:5])
        self.client.get(
            f"/api/v1/dictionary/search/?q={word}&source=en&target=uz&pos=noun",
            name="/api/v1/dictionary/search/?q=[filtered]"
        )

    @task(2)
    def get_catalog_snapshot(self):
        """Simulates initial app startup catalog fetch."""
        self.client.get(
            "/api/v1/catalog/",
            name="/api/v1/catalog/"
        )

    @task(2)
    def sync_dictionary_page(self):
        """Simulates offline database background sync pagination."""
        after_id = random.randint(0, 50000)
        self.client.get(
            f"/api/v1/dictionary/sync/?afterId={after_id}&limit=200",
            name="/api/v1/dictionary/sync/"
        )

    @task(1)
    def check_health(self):
        """Simulates load balancer and container readiness probes."""
        self.client.get(
            "/health/",
            name="/health/"
        )
