from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from app.clients.parcel import ParcelClient
from app.clients.pricing import PricingClient


class QuoteDetailsNeeded(ValueError):
    """A fee tool call needs more information from the user."""

    def __init__(self, fields: list[str]):
        super().__init__("Invalid tool arguments for fee estimate")
        self.question = "Could you provide the " + ", ".join(fields) + "?"


QUOTE_FIELDS = {
    "pickup_city": "pickup city",
    "delivery_city": "delivery city",
    "weight": "parcel weight in kg",
    "service_type": "service type (Standard or Express)",
    "size": "parcel size (Small, Medium or Large)",
}


class TrackParcelArgs(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
    )

    tracking_number: str = Field(min_length=1)


class EstimateFeeArgs(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
    )

    pickup_city: str = Field(min_length=1)
    delivery_city: str = Field(min_length=1)
    weight: float = Field(gt=0, allow_inf_nan=False)
    service_type: Literal["STANDARD", "EXPRESS"]
    size: Literal["SMALL", "MEDIUM", "LARGE"]


async def execute_tool(
    name: str,
    arguments: dict,
    access_token: str,
    parcel_client: ParcelClient,
    pricing_client: PricingClient,
) -> dict:
    try:
        if name == "track_parcel":
            args = TrackParcelArgs.model_validate(arguments)

            return await parcel_client.track_parcel(
                tracking_number=args.tracking_number,
                access_token=access_token,
            )

        if name == "estimate_fee":
            args = EstimateFeeArgs.model_validate(arguments)

            request_data = {
                "pickupCity": args.pickup_city,
                "deliveryCity": args.delivery_city,
                "weight": args.weight,
                "serviceType": args.service_type,
                "size": args.size,
            }

            return await pricing_client.estimate_fee(
                request_data=request_data,
                access_token=access_token,
            )

    except ValidationError as exc:
        if name == "estimate_fee":
            invalid = {error["loc"][0] for error in exc.errors() if error["loc"]}
            needed = [label for field, label in QUOTE_FIELDS.items() if field in invalid]
            if needed:
                raise QuoteDetailsNeeded(needed) from exc
        raise ValueError("Invalid tool arguments") from exc

    raise ValueError("Unsupported tool")
