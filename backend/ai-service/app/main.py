from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.security import AuthenticatedUser, require_user

app = FastAPI(
    title="SwiftDrop AI Service",
    description="AI-powered parcel delivery assistant",
    version="0.1.0",
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


@app.post("/api/ai/chat", tags=["AI"])
async def chat(
    payload: ChatRequest,
    user: Annotated[AuthenticatedUser, Depends(require_user)],
):
    raise HTTPException(
        status_code=501,
        detail="AI chat is not implemented yet",
    )
