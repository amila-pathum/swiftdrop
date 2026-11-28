import pytest

from app.agent.tools import TOOLS


def test_tool_names_are_unique():
    names = [tool["name"] for tool in TOOLS]

    assert len(names) == len(set(names))


@pytest.mark.parametrize("role", ["CUSTOMER", "COURIER", "MERCHANT", "DISPATCHER", "ADMIN"])
def test_expected_tools_are_available(role):
    expected = {"track_parcel", "estimate_fee"}
    if role in {"ADMIN", "DISPATCHER"}:
        expected.update({"get_delivery_report", "get_dispatch_workload"})
    if role == "ADMIN":
        expected.update({"get_account_count", "get_pending_approvals", "get_delivery_zone"})
    assert {tool["name"] for tool in TOOLS if role in tool["roles"]} == expected


def test_tool_parameter_schemas():
    for tool in TOOLS:
        parameters = tool["parameters"]

        assert parameters["type"] == "object"
        required = {
            "track_parcel": ["tracking_number"],
            "get_delivery_report": ["start_date", "end_date"],
            "get_delivery_zone": ["city"],
        }
        assert parameters.get("required", []) == required.get(tool["name"], [])
        assert parameters["additionalProperties"] is False
