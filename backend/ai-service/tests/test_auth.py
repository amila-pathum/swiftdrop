import base64
from datetime import UTC, datetime, timedelta
from unittest.mock import AsyncMock

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


def test_valid_token(monkeypatch):
    token = make_token()
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    agent = AsyncMock(return_value="What is your tracking number?")
    monkeypatch.setattr("app.main.answer_message", agent)

    with TestClient(app) as chat_client:
        response = chat_client.post(
            "/api/ai/chat",
            json={"message": "Where is my parcel?"},
            headers={"Authorization": f"Bearer {token}"},
        )
    assert response.status_code == 200
    assert response.json() == {"reply": "What is your tracking number?"}
    agent.assert_awaited_once()
    assert agent.await_args.kwargs["message"] == "Where is my parcel?"
    assert agent.await_args.kwargs["access_token"] == token
