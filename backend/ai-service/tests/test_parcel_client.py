import asyncio

import httpx
import pytest
from app.clients.parcel import ParcelClient, ParcelServiceError


def test_track_parcel_uses_backend_and_passes_token():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/parcels/track/SD12345"
        assert request.headers["Authorization"] == "Bearer test-token"

        return httpx.Response(
            200,
            json={
                "trackingNumber": "SD12345",
                "status": "IN_TRANSIT",
            },
        )

    async def scenario():
        transport = httpx.MockTransport(handler)

        async with httpx.AsyncClient(
            base_url="http://parcel-test",
            transport=transport,
        ) as http_client:
            client = ParcelClient(
                http_client,
                tracking_path="/api/parcels/track/{tracking_number}",
            )

            return await client.track_parcel("SD12345", "test-token")

    assert asyncio.run(scenario())["status"] == "IN_TRANSIT"


def test_parcel_service_failure_is_handled():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            503,
            text="Sensitive internal details",
        )

    async def scenario():
        async with httpx.AsyncClient(
            base_url="http://parcel-test",
            transport=httpx.MockTransport(handler),
        ) as http_client:
            client = ParcelClient(
                http_client,
                tracking_path="/api/parcels/track/{tracking_number}",
            )

            await client.track_parcel("SD12345", "test-token")

    with pytest.raises(
        ParcelServiceError,
        match="Unable to retrieve parcel tracking",
    ):
        asyncio.run(scenario())
