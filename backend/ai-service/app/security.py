import base64
import binascii
import os
from dataclasses import dataclass
from typing import Annotated

import jwt
from fastapi import Header, HTTPException


@dataclass(frozen=True)
class AuthenticatedUser:
    user_id: int
    email: str
    role: str


def require_user(
    authorization: Annotated[str | None, Header()] = None,
) -> AuthenticatedUser:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(
            status_code=401,
            detail="Authentication is required",
        )

    token = authorization[7:].strip()
    if not token:
        raise HTTPException(
            status_code=401,
            detail="Authentication is required",
        )

    encoded_secret = os.getenv("JWT_SECRET")
    if not encoded_secret:
        raise RuntimeError("JWT_SECRET must be configured")

    try:
        signing_key = base64.b64decode(encoded_secret, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise RuntimeError("JWT_SECRET must be valid Base64") from exc

    if len(signing_key) < 32:
        raise RuntimeError("JWT_SECRET must contain at least 256 bits")

    if len(signing_key) >= 64:
        algorithm = "HS512"
    elif len(signing_key) >= 48:
        algorithm = "HS384"
    else:
        algorithm = "HS256"

    try:
        claims = jwt.decode(
            token,
            signing_key,
            algorithms=[algorithm],
            options={"require": ["sub", "email", "role", "iat", "exp"]},
        )

        user_id = int(claims["sub"])
        email = claims["email"]
        role = claims["role"]

        if (
            user_id <= 0
            or not isinstance(email, str)
            or not email.strip()
            or not isinstance(role, str)
            or not role.strip()
        ):
            raise ValueError("Invalid identity claims")

        return AuthenticatedUser(
            user_id=user_id,
            email=email,
            role=role,
        )

    except (jwt.PyJWTError, TypeError, ValueError) as exc:
        raise HTTPException(
            status_code=401,
            detail="Invalid or expired access token",
        ) from exc
