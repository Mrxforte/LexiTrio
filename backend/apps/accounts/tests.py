from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase


class AuthenticationTests(APITestCase):
    def test_register_login_and_logout(self):
        response = self.client.post(
            "/api/v1/auth/register/",
            {"email": " Learner@example.com ", "password": "A-valid-password-92"},
        )
        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["email"], "learner@example.com")
        self.assertTrue(response.data["token"])

        login = self.client.post(
            "/api/v1/auth/login/",
            {"email": "LEARNER@example.com", "password": "A-valid-password-92"},
        )
        self.assertEqual(login.status_code, 200)
        self.assertEqual(login.data["token"], response.data["token"])

        self.client.credentials(HTTP_AUTHORIZATION=f"Token {login.data['token']}")
        logout = self.client.post("/api/v1/auth/logout/")
        self.assertEqual(logout.status_code, 204)
        self.assertFalse(
            Token.objects.filter(user__email="learner@example.com").exists()
        )

    def test_duplicate_email_is_rejected_case_insensitively(self):
        self.client.post(
            "/api/v1/auth/register/",
            {"email": "learner@example.com", "password": "A-valid-password-92"},
        )
        response = self.client.post(
            "/api/v1/auth/register/",
            {"email": "LEARNER@example.com", "password": "A-valid-password-92"},
        )
        self.assertEqual(response.status_code, 400)
