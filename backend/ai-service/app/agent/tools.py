from app.agent.operations import OPERATIONS

# Public tracking and price quotes are available to all five dashboard roles.
DASHBOARD_ROLES = frozenset({"CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN"})

TOOLS = [
    {
        "name": "track_parcel",
        "roles": DASHBOARD_ROLES,
        "description": (
            "Get the current delivery status of a parcel using its tracking number."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "tracking_number": {
                    "type": "string",
                    "description": "The parcel tracking number",
                }
            },
            "required": ["tracking_number"],
            "additionalProperties": False,
        },
    },
    {
        "name": "estimate_fee",
        "roles": DASHBOARD_ROLES,
        "description": (
            "Estimate a SwiftDrop delivery fee. You need the pickup city, "
            "delivery city, parcel weight in kilograms, service type "
            "(STANDARD or EXPRESS), and parcel size (SMALL, MEDIUM, or LARGE). "
            "Supply only details given by the user in this conversation. Omit "
            "unknown fields so the assistant can ask for them. Never assume defaults."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "pickup_city": {
                    "type": "string",
                    "description": "City where the parcel will be picked up",
                },
                "delivery_city": {
                    "type": "string",
                    "description": "City where the parcel will be delivered",
                },
                "weight": {
                    "type": "number",
                    "exclusiveMinimum": 0,
                    "description": "Parcel weight in kilograms",
                },
                "service_type": {
                    "type": "string",
                    "enum": ["STANDARD", "EXPRESS"],
                    "description": "Delivery speed selected by the user",
                },
                "size": {
                    "type": "string",
                    "enum": ["SMALL", "MEDIUM", "LARGE"],
                    "description": "Parcel size selected by the user",
                },
            },
            "additionalProperties": False,
        },
    },
]

TOOLS.extend({
    "name": name,
    "roles": tool.roles,
    "description": tool.description,
    "parameters": tool.arguments.model_json_schema(),
} for name, tool in OPERATIONS.items())
