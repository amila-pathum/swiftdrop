import asyncio
from unittest.mock import AsyncMock, Mock

import pytest

from app.agent.executor import execute_tool


def make_clients():
    parcel = Mock()
    pricing = Mock()

    parcel.track_parcel = AsyncMock(return_value={"status": "IN_TRANSIT"})
    pricing.estimate_fee = AsyncMock(
        return_value={"amount": 450, "currency": "LKR", "breakdown": {}}
    )

    return parcel, pricing


def test_track_parcel_execution():
    parcel, pricing = make_clients()

    result = asyncio.run(
        execute_tool(
            "track_parcel",
            {"tracking_number": "SD123"},
            "user-jwt",
            parcel,
            pricing,
        )
    )

    assert result["status"] == "IN_TRANSIT"
    parcel.track_parcel.assert_awaited_once_with(
        tracking_number="SD123",
        access_token="user-jwt",
    )
    pricing.estimate_fee.assert_not_awaited()


def test_estimate_fee_execution():
    parcel, pricing = make_clients()

    result = asyncio.run(
        execute_tool(
            "estimate_fee",
            {
                "pickup_city": "Colombo",
                "delivery_city": "Kandy",
                "weight": 2.0,
                "service_type": "EXPRESS",
                "size": "MEDIUM",
            },
            "user-jwt",
            parcel,
            pricing,
        )
    )

    assert result["amount"] == 450
    pricing.estimate_fee.assert_awaited_once_with(
        request_data={
            "pickupCity": "Colombo",
            "deliveryCity": "Kandy",
            "weight": 2.0,
            "serviceType": "EXPRESS",
            "size": "MEDIUM",
        },
        access_token="user-jwt",
    )
    parcel.track_parcel.assert_not_awaited()


def test_invalid_arguments_are_rejected():
    parcel, pricing = make_clients()

    with pytest.raises(ValueError, match="Invalid tool arguments"):
        asyncio.run(
            execute_tool(
                "estimate_fee",
                {
                    "pickup_city": "Colombo",
                    "delivery_city": "Kandy",
                    "weight": -2,
                    "service_type": "STANDARD",
                    "size": "SMALL",
                },
                "user-jwt",
                parcel,
                pricing,
            )
        )

    pricing.estimate_fee.assert_not_awaited()


def test_unknown_tool_is_rejected():
    parcel, pricing = make_clients()

    with pytest.raises(ValueError, match="Unsupported tool"):
        asyncio.run(
            execute_tool(
                "delete_all_parcels",
                {},
                "user-jwt",
                parcel,
                pricing,
            )
        )

    parcel.track_parcel.assert_not_awaited()
    pricing.estimate_fee.assert_not_awaited()
