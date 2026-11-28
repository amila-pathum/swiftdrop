import asyncio
import json

import httpx
import pytest

from app.clients.pricing import PricingClient, PricingServiceError


def test_estimate_fee():
    request_data = {
        "pickupCity": "Colombo",
        "deliveryCity": "Kandy",
        "weight": 2.0,
        "serviceType": "EXPRESS",
        "size": "MEDIUM",
    }

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "POST"
        assert request.url.path == "/api/pricing/quote"
        assert request.headers["Authorization"] == "Bearer test-token"
        assert json.loads(request.content) == request_data

        return httpx.Response(
            200,
            json={"amount": 450.00, "currency": "LKR", "breakdown": {}},
        )

    async def scenario():
        async with httpx.AsyncClient(
            base_url="http://pricing-test",
            transport=httpx.MockTransport(handler),
        ) as http_client:
            client = PricingClient(
                http_client,
                estimate_path="/api/pricing/quote",
            )
            return await client.estimate_fee(request_data, "test-token")

    result = asyncio.run(scenario())

    assert result["amount"] == 450.00
    assert result["currency"] == "LKR"


def test_pricing_service_failure():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503)

    async def scenario():
        async with httpx.AsyncClient(
            base_url="http://pricing-test",
            transport=httpx.MockTransport(handler),
        ) as http_client:
            client = PricingClient(
                http_client,
                estimate_path="/api/pricing/quote",
            )
            await client.estimate_fee(
                {
                    "pickupCity": "Colombo",
                    "deliveryCity": "Kandy",
                    "weight": 2.0,
                    "serviceType": "STANDARD",
                    "size": "SMALL",
                },
                "test-token",
            )

    with pytest.raises(PricingServiceError):
        asyncio.run(scenario())
