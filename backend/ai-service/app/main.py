import os
from contextlib import AsyncExitStack, asynccontextmanager
from typing import Annotated

import httpx
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from google import genai
from google.genai import errors
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.agent.agent import ChatTurn, answer_message
from app.clients.operations import OperationsClient
from app.clients.parcel import ParcelClient
from app.clients.pricing import PricingClient
from app.security import AuthenticatedUser, require_user


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with AsyncExitStack() as resources:
        parcel_http = await resources.enter_async_context(httpx.AsyncClient(
            base_url=os.getenv("PARCEL_SERVICE_URL", "http://localhost:8082")
        ))
        pricing_http = await resources.enter_async_context(httpx.AsyncClient(
            base_url=os.getenv("PRICING_SERVICE_URL", "http://localhost:8086")
        ))
        app.state.parcel = ParcelClient(parcel_http, "/api/parcels/track/{tracking_number}")
        app.state.pricing = PricingClient(pricing_http, "/api/pricing/quote")
        gateway_url = os.getenv("SWIFTDROP_GATEWAY_URL") or f"http://localhost:{os.getenv('GATEWAY_PORT', '8080')}"
        gateway_http = await resources.enter_async_context(httpx.AsyncClient(base_url=gateway_url))
        app.state.operations = OperationsClient(gateway_http)
        app.state.gemini = None
        if api_key := os.getenv("GEMINI_API_KEY"):
            gemini = resources.enter_context(genai.Client(api_key=api_key))
            await resources.enter_async_context(gemini.aio)
            app.state.gemini = gemini
        yield


app = FastAPI(
    title="SwiftDrop AI Service",
    description="AI-powered parcel delivery assistant",
    version="0.1.0",
    lifespan=lifespan,
)


@app.exception_handler(StarletteHTTPException)
async def http_error_handler(
    request: Request,
    exc: StarletteHTTPException,
):
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": str(exc.detail)},
        headers=exc.headers,
    )


@app.get("/health", tags=["Health"])
def health_check():
    return {"status": "UP"}


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)
    history: tuple[ChatTurn, ...] = Field(default=(), max_length=8)


class ChatResponse(BaseModel):
    reply: str


@app.post("/api/ai/chat", tags=["AI"])
async def chat(
    payload: ChatRequest,
    request: Request,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
) -> ChatResponse:
    services = request.app.state
    if services.gemini is None:
        raise HTTPException(status_code=503, detail="Assistant is not configured")

    try:
        reply = await answer_message(
            message=payload.message,
            access_token=user.access_token,
            role=user.role,
            gemini_client=services.gemini,
            parcel_client=services.parcel,
            pricing_client=services.pricing,
            operations_client=services.operations,
            history=payload.history,
        )
    except (errors.APIError, httpx.RequestError) as exc:
        raise HTTPException(status_code=503, detail="Assistant is unavailable") from exc
    return ChatResponse(reply=reply)
