"""Read-only operational summaries from the same gateway APIs as the dashboards."""

import asyncio
from datetime import date
from typing import Annotated

import httpx
from pydantic import BaseModel, Field, TypeAdapter, ValidationError


class OperationsUnavailable(Exception):
    pass


Count = Annotated[int, Field(strict=True, ge=0)]


class PageTotal(BaseModel):
    totalElements: Count


class DeliveryDay(BaseModel):
    delivered: Count
    failed: Count


class DispatchReport(BaseModel):
    deliveriesPerDay: list[DeliveryDay]
    successRate: float = Field(ge=0, le=100)


class CourierLoad(BaseModel):
    courierId: int
    activeParcelCount: Count


class Zone(BaseModel):
    name: str
    localities: list[str]
    active: bool


class OperationsClient:
    def __init__(self, http: httpx.AsyncClient):
        self.http = http

    async def _get(self, path, token, response_type, **params):
        try:
            response = await self.http.get(
                path, params=params, headers={"Authorization": f"Bearer {token}"}, timeout=5,
            )
            response.raise_for_status()
            return TypeAdapter(response_type).validate_json(response.content)
        except (httpx.HTTPError, ValidationError) as exc:
            raise OperationsUnavailable from exc

    async def pending_approvals(self, token):
        async def pending_count(path):
            try:
                rows = await self._get(path, token, list[dict], status="PENDING")
                return str(len(rows))
            except OperationsUnavailable:
                return "unavailable"

        merchants, couriers = await asyncio.gather(
            pending_count("/api/merchants/admin"),
            pending_count("/api/couriers/admin/applications"),
        )
        return (f"Pending merchant applications: {merchants} (Merchant Approvals).\n"
                f"Pending courier applications: {couriers} (Courier Approvals).")

    async def delivery_report(self, token, start_date: date, end_date: date):
        if start_date > end_date:
            return "Please give a start date on or before the end date."
        report = await self._get("/api/dispatch/reports/summary", token, DispatchReport,
                                 **{"from": start_date.isoformat(), "to": end_date.isoformat()})
        delivered = sum(day.delivered for day in report.deliveriesPerDay)
        failed = sum(day.failed for day in report.deliveriesPerDay)
        return (f"Dispatch report for {start_date} to {end_date} (UTC): "
                f"{delivered} delivered, {failed} failed. Success rate: {report.successRate:g}%.")

    async def dispatch_workload(self, token):
        try:
            page = await self._get("/api/dispatch/parcels/unassigned", token, PageTotal, pageSize=1)
            waiting = str(page.totalElements)
        except OperationsUnavailable:
            waiting = "unavailable"
        try:
            couriers = await self._get("/api/dispatch/couriers", token, list[CourierLoad])
            workloads = [f"Courier #{c.courierId}: {c.activeParcelCount}" for c in couriers]
            workload = ", ".join(workloads) or "No courier profiles."
        except OperationsUnavailable:
            workload = "unavailable"
        return f"Waiting parcels: {waiting}.\nActive deliveries per courier: {workload}"

    async def account_count(self, token, role=None, status=None):
        filters = {k: v for k, v in {"role": role, "status": status}.items() if v}
        page = await self._get("/api/admin/users", token, PageTotal, size=1, **filters)
        description = " ".join(v.lower() for v in (status, role) if v) or "all"
        return f"User Management shows {page.totalElements} matching accounts ({description})."

    async def delivery_zone(self, token, city):
        zones = await self._get("/api/zones", token, list[Zone], includeInactive="true")
        matches = [f"{z.name} ({'active' if z.active else 'inactive'})" for z in zones
                   if any(city.casefold() == locality.strip().casefold() for locality in z.localities)]
        if not matches:
            return f"No delivery zone lists {city} as a covered locality."
        return f"Delivery zones covering {city}: {', '.join(matches)}."

