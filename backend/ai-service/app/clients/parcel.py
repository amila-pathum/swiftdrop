from urllib.parse import quote

import httpx


class ParcelServiceError(Exception):
    """Raised when parcel tracking information is unavailable."""


class ParcelClient:
    def __init__(self, http_client: httpx.AsyncClient, tracking_path: str):
        self.http_client = http_client
        self.tracking_path = tracking_path

    async def track_parcel(self, tracking_number: str, access_token: str) -> dict:
        if not tracking_number or not access_token:
            raise ValueError("Tracking number and access token are required")

        path = self.tracking_path.format(
            tracking_number=quote(tracking_number, safe="")
        )

        try:
            response = await self.http_client.get(
                path,
                headers={"Authorization": f"Bearer {access_token}"},
                timeout=5.0,
            )

            response.raise_for_status()
            data = response.json()

            if not isinstance(data, dict):
                raise TypeError("Expected JSON object")

            return data

        except (httpx.HTTPError, ValueError, TypeError) as exc:
            raise ParcelServiceError("Unable to retrieve parcel tracking") from exc
