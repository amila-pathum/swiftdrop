from pydantic import BaseModel, ConfigDict, Field, ValidationError

from app.clients.parcel import ParcelClient
from app.clients.pricing import PricingClient


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

    pickup_zone: str = Field(min_length=1)
    dropoff_zone: str = Field(min_length=1)
    weight_kg: float = Field(gt=0, allow_inf_nan=False)


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
                "pickupZone": args.pickup_zone,
                "dropoffZone": args.dropoff_zone,
                "weightKg": args.weight_kg,
            }

            return await pricing_client.estimate_fee(
                request_data=request_data,
                access_token=access_token,
            )

    except ValidationError as exc:
        raise ValueError("Invalid tool arguments") from exc

    raise ValueError("Unsupported tool")
