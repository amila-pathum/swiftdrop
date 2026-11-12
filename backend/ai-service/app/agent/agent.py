from google.genai import types

from app.agent.executor import execute_tool
from app.agent.tools import TOOLS
from app.clients.parcel import ParcelServiceError
from app.clients.pricing import PricingServiceError

SYSTEM_PROMPT = """
You are SwiftDrop's parcel delivery assistant.

Use available tools for parcel tracking and fee estimation.
Never invent parcel statuses, prices, or delivery information.
If required information is missing, ask the user for it.
Never reveal private customer data.
Use tool results only to answer the user's question.
Keep responses clear and concise.
"""

TOOL = types.Tool(
    function_declarations=[
        types.FunctionDeclaration(
            name=tool["name"],
            description=tool["description"],
            parameters_json_schema=tool["parameters"],
        )
        for tool in TOOLS
    ]
)

SAFE_FIELDS = {
    "track_parcel": {"trackingNumber", "status"},
    "estimate_fee": {"totalFee", "currency"},
}


async def answer_message(
    message,
    access_token,
    gemini_client,
    parcel_client,
    pricing_client,
    model="gemini-3.5-flash-lite",
):
    config = types.GenerateContentConfig(
        system_instruction=SYSTEM_PROMPT,
        tools=[TOOL],
        automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True),
    )

    user_content = types.Content(
        role="user",
        parts=[types.Part.from_text(text=message)],
    )

    # 1. Ask Gemini to understand the user request.
    first_response = await gemini_client.aio.models.generate_content(
        model=model,
        contents=[user_content],
        config=config,
    )

    calls = first_response.function_calls or []

    # Gemini may answer directly or ask for missing information.
    if not calls:
        return first_response.text or "Could you clarify your request?"

    # Limit the initial implementation to one tool per request.
    if len(calls) != 1:
        return "Please ask one delivery question at a time."

    call = calls[0]

    if call.name not in SAFE_FIELDS:
        return "I cannot perform that operation."

    # 2. Execute the selected tool using the user's JWT.
    try:
        result = await execute_tool(
            name=call.name,
            arguments=dict(call.args or {}),
            access_token=access_token,
            parcel_client=parcel_client,
            pricing_client=pricing_client,
        )
    except (ValueError, ParcelServiceError, PricingServiceError):
        return "I couldn't retrieve that information. Please check your details."

    # 3. Filter backend data before sending it to Gemini.
    safe_result = {
        key: value for key, value in result.items() if key in SAFE_FIELDS[call.name]
    }

    if not safe_result:
        return "The delivery information is currently unavailable."

    tool_response = types.Content(
        role="tool",
        parts=[
            types.Part.from_function_response(
                name=call.name,
                response={"result": safe_result},
            )
        ],
    )

    # 4. Ask Gemini to explain the tool result.
    final_response = await gemini_client.aio.models.generate_content(
        model=model,
        contents=[
            user_content,
            first_response.candidates[0].content,
            tool_response,
        ],
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            tools=[TOOL],
            tool_config=types.ToolConfig(
                function_calling_config=types.FunctionCallingConfig(mode="NONE")
            ),
        ),
    )

    return final_response.text or "Unable to generate a response."
