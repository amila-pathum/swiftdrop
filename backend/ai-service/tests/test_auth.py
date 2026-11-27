import base64
from datetime import UTC, datetime, timedelta
from unittest.mock import AsyncMock, Mock

import jwt
import pytest
from fastapi.testclient import TestClient
from google.genai import types

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
    assert agent.await_args.kwargs["history"] == ()


def test_fee_followup_uses_prior_turn_for_quote(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    question = "How much from Colombo to Kandy, Express, medium parcel?"
    prompt = "How much does it weigh?"
    call = types.FunctionCall(name="estimate_fee", args={
        "pickup_city": "Colombo", "delivery_city": "Kandy", "weight": 2,
        "service_type": "EXPRESS", "size": "MEDIUM",
    })
    selection = types.Content(role="model", parts=[types.Part(function_call=call)])
    generate = AsyncMock(side_effect=[
        Mock(function_calls=[call], candidates=[Mock(content=selection)]),
        Mock(text="The estimate is LKR 450."),
    ])
    quote = AsyncMock(return_value={"amount": 450, "currency": "LKR"})
    token = make_token()
    with TestClient(app) as chat_client:
        monkeypatch.setattr(app.state.gemini.aio.models, "generate_content", generate)
        monkeypatch.setattr(app.state.pricing, "estimate_fee", quote)
        response = chat_client.post(
            "/api/ai/chat",
            json={"message": "2 kg", "history": [{"message": question, "reply": prompt}]},
            headers={"Authorization": f"Bearer {token}"},
        )
    assert response.status_code == 200
    assert response.json() == {"reply": "The estimate is LKR 450."}
    for step in generate.await_args_list:
        context = step.kwargs["contents"][:3]
        assert [(item.role, item.parts[0].text) for item in context] == [
            ("user", question), ("model", prompt), ("user", "2 kg"),
        ]
    quote.assert_awaited_once_with(
        request_data={"pickupCity": "Colombo", "deliveryCity": "Kandy", "weight": 2,
                      "serviceType": "EXPRESS", "size": "MEDIUM"},
        access_token=token,
    )
