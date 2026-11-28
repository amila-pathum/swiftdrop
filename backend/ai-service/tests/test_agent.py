import asyncio
from unittest.mock import AsyncMock, Mock

import httpx
import pytest
from google.genai import types

from app.agent.agent import answer_message
from app.agent.tools import TOOLS


@pytest.mark.parametrize(("role", "menu"), [
    ("CUSTOMER", "Confirm Delivery"),
    ("MERCHANT", "Generate New API Key"),
    ("COURIER", "Open Delivery"),
    ("DISPATCHER", "Waiting parcels"),
    ("ADMIN", "Merchant Approvals"),
])
def test_site_help_supplies_the_signed_in_roles_guide(role, menu):
    gemini = Mock()
    gemini.aio.models.generate_content = AsyncMock(return_value=Mock(
        function_calls=[], text=f"Select {menu}.",
    ))
    parcel, pricing = AsyncMock(), AsyncMock()
    reply = asyncio.run(answer_message(
        "Can I know how to make a new delivery in this site?", "jwt", gemini,
        parcel, pricing, role=role,
    ))
    gemini.aio.models.generate_content.assert_awaited_once()
    prompt = gemini.aio.models.generate_content.await_args.kwargs["config"].system_instruction
    assert menu in prompt
    assert ("Confirm Delivery" in prompt) == (role == "CUSTOMER")
    assert ("Merchant Approvals" in prompt) == (role == "ADMIN")
    assert reply == f"Select {menu}."
    parcel.track_parcel.assert_not_awaited()
    pricing.estimate_fee.assert_not_awaited()


def test_quote_survives_failure_to_generate_final_reply():
    call = types.FunctionCall(name="estimate_fee", args={
        "pickup_city": "Kandy", "delivery_city": "Colombo", "weight": 1,
        "service_type": "STANDARD", "size": "SMALL",
    })
    selection = types.Content(role="model", parts=[types.Part(function_call=call)])
    gemini = Mock()
    gemini.aio.models.generate_content = AsyncMock(side_effect=[
        Mock(function_calls=[call], candidates=[Mock(content=selection)]),
        httpx.ReadTimeout("Model unavailable"),
    ])
    pricing = AsyncMock()
    pricing.estimate_fee.return_value = {"currency": "LKR", "amount": 700}
    reply = asyncio.run(answer_message(
        "kandy,colombo,1kg,standard,small", "user-jwt", gemini, AsyncMock(), pricing,
        role="CUSTOMER",
    ))
    assert reply == "Estimated delivery fee: LKR 700.00."
    pricing.estimate_fee.assert_awaited_once()


def test_route_only_quote_asks_for_details_before_contacting_pricing():
    model = Mock()
    model.aio.models.generate_content = AsyncMock(return_value=Mock(
        function_calls=[types.FunctionCall(name="estimate_fee", args={
            "pickup_city": "Colombo", "delivery_city": "Kandy",
        })]
    ))
    pricing = AsyncMock()
    reply = asyncio.run(answer_message(
        "How much from Colombo to Kandy?", "user-jwt", model, AsyncMock(), pricing,
        role="CUSTOMER",
    ))
    assert "weight" in reply and "service type" in reply and "size" in reply
    assert "city" not in reply
    pricing.estimate_fee.assert_not_awaited()


def test_gemini_selects_parcel_tool():
    function_call = types.FunctionCall(
        name="track_parcel",
        args={"tracking_number": "SD123"},
    )

    first_response = Mock()
    first_response.function_calls = [function_call]
    first_response.candidates = [
        Mock(
            content=types.Content(
                role="model",
                parts=[types.Part(function_call=function_call)],
            )
        )
    ]

    final_response = Mock()
    final_response.text = "Your parcel SD123 is in transit."

    gemini = Mock()
    gemini.aio.models.generate_content = AsyncMock(
        side_effect=[first_response, final_response]
    )

    parcel = Mock()
    parcel.track_parcel = AsyncMock(
        return_value={
            "trackingNumber": "SD123",
            "status": "IN_TRANSIT",
            "phoneNumber": "PRIVATE",
        }
    )

    pricing = Mock()
    pricing.estimate_fee = AsyncMock()

    answer = asyncio.run(
        answer_message(
            message="Where is my parcel SD123?",
            access_token="user-jwt",
            role="CUSTOMER",
            gemini_client=gemini,
            parcel_client=parcel,
            pricing_client=pricing,
        )
    )

    assert answer == "Your parcel SD123 is in transit."

    parcel.track_parcel.assert_awaited_once_with(
        tracking_number="SD123",
        access_token="user-jwt",
    )

    pricing.estimate_fee.assert_not_awaited()

    second_call = gemini.aio.models.generate_content.call_args_list[1]
    tool_part = second_call.kwargs["contents"][2].parts[0]

    assert tool_part.function_response.response == {
        "result": {
            "trackingNumber": "SD123",
            "status": "IN_TRANSIT",
        }
    }


def test_model_cannot_invoke_tool_excluded_for_role(monkeypatch):
    # Exercise a narrower tool policy without changing the shared pricing policy.
    quote = next(tool for tool in TOOLS if tool["name"] == "estimate_fee")
    monkeypatch.setitem(quote, "roles", {"MERCHANT"})
    gemini = Mock()
    gemini.aio.models.generate_content = AsyncMock(return_value=Mock(
        function_calls=[types.FunctionCall(name="estimate_fee", args={
            "pickup_city": "Colombo", "delivery_city": "Kandy", "weight": 2,
            "service_type": "STANDARD", "size": "SMALL",
        })]
    ))
    pricing = AsyncMock()
    answer = asyncio.run(answer_message(
        "Estimate this fee", "user-jwt", gemini, AsyncMock(), pricing, role="COURIER",
    ))
    offered = gemini.aio.models.generate_content.await_args.kwargs["config"].tools[0]
    assert [tool.name for tool in offered.function_declarations] == ["track_parcel"]
    assert answer == "I cannot perform that operation."
    pricing.estimate_fee.assert_not_awaited()
