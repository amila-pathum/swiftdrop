import base64
from datetime import UTC, datetime, timedelta

import jwt
import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
TEST_KEY = b"T" * 32


@pytest.fixture(autouse=True)
def configure_jwt_secret(monkeypatch):
    monkeypatch.setenv(
        "JWT_SECRET",
        base64.b64encode(TEST_KEY).decode(),
    )


def make_token(*, expired=False, signing_key=TEST_KEY):
    now = datetime.now(UTC)

    return jwt.encode(
        {
            "sub": "123",
            "email": "student@example.com",
            "role": "CUSTOMER",
            "iat": now,
            "exp": (
                now - timedelta(minutes=1) if expired else now + timedelta(minutes=10)
            ),
        },
        signing_key,
        algorithm="HS256",
    )


def test_missing_token():
    response = client.post(
        "/api/ai/chat",
        json={"message": "Hello"},
    )
    assert response.status_code == 401


def test_invalid_signature():
    token = make_token(signing_key=b"X" * 32)

    response = client.post(
        "/api/ai/chat",
        json={"message": "Hello"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 401


def test_expired_token():
    token = make_token(expired=True)

    response = client.post(
        "/api/ai/chat",
        json={"message": "Hello"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 401


def test_valid_token():
    token = make_token()

    response = client.post(
        "/api/ai/chat",
        json={"message": "Hello"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 501
    assert response.json() == {"error": "AI chat is not implemented yet"}
