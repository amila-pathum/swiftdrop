import httpx


class PricingServiceError(Exception):
    """Raised when a delivery fee estimate is unavailable."""


class PricingClient:
    def __init__(
        self,
        http_client: httpx.AsyncClient,
        estimate_path: str,
    ):
        self.http_client = http_client
        self.estimate_path = estimate_path

    async def estimate_fee(
        self,
        request_data: dict,
        access_token: str,
    ) -> dict:
        if not access_token:
            raise ValueError("Access token is required")

        try:
            response = await self.http_client.post(
                self.estimate_path,
                json=request_data,
                headers={"Authorization": f"Bearer {access_token}"},
                timeout=5.0,
            )

            response.raise_for_status()

            data = response.json()

            if not isinstance(data, dict):
                raise TypeError("Expected JSON object")

            return data

        except (httpx.HTTPError, ValueError, TypeError) as exc:
            raise PricingServiceError("Unable to estimate delivery fee") from exc
