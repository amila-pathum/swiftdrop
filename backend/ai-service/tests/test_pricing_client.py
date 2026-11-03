import asyncio
import json

import httpx
import pytest

from app.clients.pricing import PricingClient, PricingServiceError


def test_estimate_fee():
    request_data = {
        "pickupZone": "COLOMBO",
        "dropoffZone": "KANDY",
        "weightKg": 2.0,
    }

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "POST"
        assert request.url.path == "/api/pricing/estimate"
        assert request.headers["Authorization"] == "Bearer test-token"
        assert json.loads(request.content) == request_data

        return httpx.Response(
            200,
            json={"totalFee": 450.00, "currency": "LKR"},
        )

    async def scenario():
        async with httpx.AsyncClient(
            base_url="http://pricing-test",
            transport=httpx.MockTransport(handler),
        ) as http_client:
            client = PricingClient(
                http_client,
                estimate_path="/api/pricing/estimate",
            )
            return await client.estimate_fee(request_data, "test-token")

    result = asyncio.run(scenario())

    assert result["totalFee"] == 450.00
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
                estimate_path="/api/pricing/estimate",
            )
            await client.estimate_fee({"weightKg": 2.0}, "test-token")

    with pytest.raises(PricingServiceError):
        asyncio.run(scenario())
