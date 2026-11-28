"""Role-scoped Gemini tools for live dashboard questions."""

from collections.abc import Callable
from dataclasses import dataclass
from datetime import date
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from app.clients.operations import OperationsClient


class NoFilters(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class ReportPeriod(NoFilters):
    start_date: date = Field(description="Inclusive first UTC date (YYYY-MM-DD)")
    end_date: date = Field(description="Inclusive last UTC date (YYYY-MM-DD)")


class AccountFilters(NoFilters):
    role: Literal["CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN"] | None = None
    status: Literal["ACTIVE", "PENDING_APPROVAL", "SUSPENDED", "DISABLED"] | None = None


class City(NoFilters):
    city: str = Field(min_length=1, max_length=100)


@dataclass(frozen=True)
class OperationsTool:
    roles: frozenset[str]
    description: str
    arguments: type[BaseModel]
    read: Callable


ADMIN = frozenset({"ADMIN"})
DISPATCH = frozenset({"ADMIN", "DISPATCHER"})

OPERATIONS = {
    "get_pending_approvals": OperationsTool(
        ADMIN, "Count pending merchant and courier applications awaiting admin review.",
        NoFilters, OperationsClient.pending_approvals,
    ),
    "get_delivery_report": OperationsTool(
        DISPATCH, "Get delivered and failed counts and success rate for an inclusive UTC date range.",
        ReportPeriod, OperationsClient.delivery_report,
    ),
    "get_dispatch_workload": OperationsTool(
        DISPATCH, "Get the number of unassigned waiting parcels and active delivery counts per courier.",
        NoFilters, OperationsClient.dispatch_workload,
    ),
    "get_account_count": OperationsTool(
        ADMIN, "Count registered accounts, optionally filtered by role and account status.",
        AccountFilters, OperationsClient.account_count,
    ),
    "get_delivery_zone": OperationsTool(
        ADMIN, "Find delivery zones covering a city, including their active or inactive status.",
        City, OperationsClient.delivery_zone,
    ),
}
