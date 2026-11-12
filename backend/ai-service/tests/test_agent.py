import asyncio
from unittest.mock import AsyncMock, Mock

from google.genai import types

from app.agent.agent import answer_message


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
