import asyncio
from unittest.mock import AsyncMock, Mock

import httpx
import pytest
from google.genai import types

from app.agent.agent import answer_message
from app.clients.operations import OperationsClient


def dashboard_call(role, tool, args, routes):
    seen = []
    token = "test-user-token"

    def respond(request):
        assert request.method == "GET"
        assert request.headers["Authorization"] == f"Bearer {token}"
        path = request.url.path
        params, status, data = routes[path]
        assert dict(request.url.params) == params
        seen.append(path)
        return httpx.Response(status, json=data)

    async def run():
        model = Mock()
        model.aio.models.generate_content = AsyncMock(return_value=Mock(
            function_calls=[types.FunctionCall(name=tool, args=args)],
        ))
        async with httpx.AsyncClient(
            base_url="http://gateway", transport=httpx.MockTransport(respond),
        ) as http:
            return await answer_message(
                "Show the requested dashboard data", token, model, AsyncMock(), AsyncMock(),
                role=role, operations_client=OperationsClient(http),
            )

    result = asyncio.run(run())
    assert sorted(seen) == sorted(routes)
    return result


@pytest.mark.parametrize("role,tool", [
    ("CUSTOMER", "get_delivery_report"),
    ("DISPATCHER", "get_pending_approvals"),
    ("COURIER", "get_delivery_zone"),
    ("MERCHANT", "get_account_count"),
])
def test_unauthorized_selection_never_calls_gateway(role, tool):
    assert "cannot perform" in dashboard_call(role, tool, {}, {})


def test_approvals_keep_partial_result_private():
    reply = dashboard_call("ADMIN", "get_pending_approvals", {}, {
        "/api/merchants/admin": ({"status": "PENDING"}, 200, [{"email": "PRIVATE"}]),
        "/api/couriers/admin/applications": ({"status": "PENDING"}, 503, {}),
    })
    assert "merchant applications: 1" in reply
    assert "courier applications: unavailable" in reply
    assert "PRIVATE" not in reply


def test_workload_uses_total_and_excludes_courier_contact_details():
    reply = dashboard_call("DISPATCHER", "get_dispatch_workload", {}, {
        "/api/dispatch/parcels/unassigned": ({"pageSize": "1"}, 200, {"totalElements": 42}),
        "/api/dispatch/couriers": ({}, 200, [
            {"courierId": 7, "activeParcelCount": 4, "phone": "PRIVATE"},
        ]),
    })
    assert "Waiting parcels: 42" in reply and "Courier #7: 4" in reply
    assert "PRIVATE" not in reply


def test_report_for_requested_period():
    period = {"start_date": "2026-11-21", "end_date": "2026-11-27"}
    reply = dashboard_call("ADMIN", "get_delivery_report", period, {
        "/api/dispatch/reports/summary": ({"from": "2026-11-21", "to": "2026-11-27"}, 200, {
            "deliveriesPerDay": [{"delivered": 3, "failed": 1}, {"delivered": 1, "failed": 0}],
            "successRate": 80,
        }),
    })
    assert "4 delivered, 1 failed" in reply and "80%" in reply


def test_filtered_account_count_uses_paged_total():
    reply = dashboard_call("ADMIN", "get_account_count", {"role": "COURIER", "status": "SUSPENDED"}, {
        "/api/admin/users": ({"role": "COURIER", "status": "SUSPENDED", "size": "1"}, 200,
                             {"totalElements": 18, "content": [{"name": "PRIVATE"}]}),
    })
    assert "18 matching accounts (suspended courier)" in reply and "PRIVATE" not in reply


def test_zone_lookup_includes_inactive_locality_matches():
    reply = dashboard_call("ADMIN", "get_delivery_zone", {"city": " kandy "}, {
        "/api/zones": ({"includeInactive": "true"}, 200, [
            {"name": "Hill Country", "active": False, "localities": ["Kandy"]},
            {"name": "Western", "active": True, "localities": ["Colombo"]},
        ]),
    })
    assert "Hill Country (inactive)" in reply and "Western" not in reply


def test_invalid_account_filters_do_not_broaden_query():
    reply = dashboard_call("ADMIN", "get_account_count", {"status": "BANANA"}, {})
    assert "check the filters" in reply
