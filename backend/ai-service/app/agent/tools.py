TOOLS = [
    {
        "name": "track_parcel",
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
        "description": (
            "Estimate the delivery fee for a parcel "
            "based on pickup zone, destination zone, "
            "and weight in kilograms."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "pickup_zone": {
                    "type": "string",
                    "description": "Parcel pickup zone",
                },
                "dropoff_zone": {
                    "type": "string",
                    "description": "Parcel destination zone",
                },
                "weight_kg": {
                    "type": "number",
                    "exclusiveMinimum": 0,
                    "description": "Parcel weight in kilograms",
                },
            },
            "required": [
                "pickup_zone",
                "dropoff_zone",
                "weight_kg",
            ],
            "additionalProperties": False,
        },
    },
]
