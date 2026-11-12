import asyncio
from unittest.mock import AsyncMock, Mock

import pytest

from app.agent.executor import execute_tool


def make_clients():
    parcel = Mock()
    pricing = Mock()

    parcel.track_parcel = AsyncMock(return_value={"status": "IN_TRANSIT"})
    pricing.estimate_fee = AsyncMock(return_value={"totalFee": 450, "currency": "LKR"})

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
                "pickup_zone": "COLOMBO",
                "dropoff_zone": "KANDY",
                "weight_kg": 2.0,
            },
            "user-jwt",
            parcel,
            pricing,
        )
    )

    assert result["totalFee"] == 450
    pricing.estimate_fee.assert_awaited_once_with(
        request_data={
            "pickupZone": "COLOMBO",
            "dropoffZone": "KANDY",
            "weightKg": 2.0,
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
                {"weight_kg": -2},
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
