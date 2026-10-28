import uuid

from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase


class SyncTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(
            username="learner@example.com",
            email="learner@example.com",
            password="A-valid-password-92",
        )
        token = Token.objects.create(user=self.user)
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {token.key}")

    def test_sync_is_idempotent_and_returns_user_snapshot(self):
        client_id = str(uuid.uuid4())
        payload = {
            "collections": [
                {
                    "clientId": client_id,
                    "title": "Travel",
                    "description": "",
                    "iconName": "flight",
                    "isCustom": True,
                    "isDeleted": False,
                    "createdAt": 1,
                    "updatedAt": 100,
                }
            ],
            "savedWords": [
                {
                    "clientId": str(uuid.uuid4()),
                    "headword": "travel",
                    "language": "en",
                    "partOfSpeech": "noun",
                    "phoneticIpa": "",
                    "definition": "A journey",
                    "translation": "sayohat",
                    "exampleSentence": "",
                    "collectionClientId": client_id,
                    "savedAt": 1,
                    "isFavorite": True,
                    "status": "NEW",
                    "notes": "",
                    "repetitionNumber": 0,
                    "intervalDays": 0,
                    "easeFactor": 2.5,
                    "nextReviewTimestamp": 2,
                    "lastReviewTimestamp": 0,
                    "lapsesCount": 0,
                    "updatedAt": 100,
                }
            ],
            "quizAttempts": [],
            "progress": {"xp": 140, "dailyGoalWords": 10, "updatedAt": 100},
            "preferences": {
                "appLanguage": "uz",
                "searchSourceLanguage": "en",
                "speechRate": 1.0,
                "speechPitch": 1.0,
                "selectedVoiceId": "",
                "autoPlayAudio": False,
                "dailyGoalWords": 10,
                "darkMode": None,
                "updatedAt": 100,
            },
        }
        first = self.client.post("/api/v1/sync/", payload, format="json")
        second = self.client.post("/api/v1/sync/", payload, format="json")

        self.assertEqual(first.status_code, 200)
        self.assertEqual(second.status_code, 200)
        self.assertEqual(len(second.data["collections"]), 1)
        self.assertEqual(len(second.data["savedWords"]), 1)
        self.assertEqual(second.data["progress"]["xp"], 140)
        self.assertEqual(second.data["preferences"]["selectedVoiceId"], "")

        payload["collections"][0]["title"] = "Stale collection"
        payload["collections"][0]["updatedAt"] = 99
        payload["savedWords"][0]["headword"] = "stale"
        payload["savedWords"][0]["updatedAt"] = 99
        payload["progress"]["xp"] = 1
        payload["progress"]["updatedAt"] = 99
        payload["preferences"]["appLanguage"] = "en"
        payload["preferences"]["updatedAt"] = 99
        stale = self.client.post("/api/v1/sync/", payload, format="json")

        self.assertEqual(stale.status_code, 200)
        self.assertEqual(stale.data["collections"][0]["title"], "Travel")
        self.assertEqual(stale.data["savedWords"][0]["headword"], "travel")
        self.assertEqual(stale.data["progress"]["xp"], 140)
        self.assertEqual(stale.data["preferences"]["appLanguage"], "uz")

        deleted = self.client.post(
            "/api/v1/sync/",
            {
                "deletedCollectionIds": [
                    {"clientId": client_id, "deletedAt": 101}
                ],
                "deletedSavedWordIds": [
                    {
                        "clientId": payload["savedWords"][0]["clientId"],
                        "deletedAt": 101,
                    }
                ],
            },
            format="json",
        )

        self.assertEqual(deleted.status_code, 200)
        self.assertTrue(deleted.data["collections"][0]["isDeleted"])
        self.assertTrue(deleted.data["savedWords"][0]["isDeleted"])

    def test_sync_requires_authentication(self):
        self.client.credentials()
        response = self.client.get("/api/v1/sync/")
        self.assertEqual(response.status_code, 401)
