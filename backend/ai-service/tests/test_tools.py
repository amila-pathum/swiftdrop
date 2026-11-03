from app.agent.tools import TOOLS


def test_tool_names_are_unique():
    names = [tool["name"] for tool in TOOLS]

    assert len(names) == len(set(names))


def test_expected_tools_are_available():
    names = {tool["name"] for tool in TOOLS}

    assert names == {"track_parcel", "estimate_fee"}


def test_tool_parameters_are_required():
    for tool in TOOLS:
        parameters = tool["parameters"]

        assert parameters["type"] == "object"
        assert parameters["required"]
        assert parameters["additionalProperties"] is False
