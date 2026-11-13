"""Run with jsonschema installed: python -m unittest discover -s verification -p test_creation_event_contract.py"""
import json
from pathlib import Path
import unittest
from jsonschema import Draft202012Validator, FormatChecker

CONTRACTS = Path(__file__).resolve().parents[3] / "contracts" / "events"

class CreationEventContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        schema = json.loads((CONTRACTS / "parcel.status-changed.v2.schema.json").read_text())
        Draft202012Validator.check_schema(schema)
        cls.validator = Draft202012Validator(schema, format_checker=FormatChecker())

    def event(self, old=None, new="CREATED"):
        return dict(eventId="5a823ce5-994b-48d2-9707-e08ff9a7f872",
                    eventType="ParcelStatusChanged", occurredAt="2026-11-13T10:30:00Z",
                    trackingNumber="SDTEST001", parcelId="447143ec-977b-4d96-944a-f43e238f6265",
                    customerId="42", merchantId=None, merchantOrderReference=None,
                    courierId=None, oldStatus=old, newStatus=new)

    def test_creation_accepts_null_previous_status(self):
        self.validator.validate(self.event())

    def test_existing_transition_remains_valid(self):
        self.validator.validate(self.event("CREATED", "PICKED_UP"))

    def test_null_previous_status_rejected_for_other_statuses(self):
        for status in ["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED", "CANCELLED"]:
            with self.subTest(status=status):
                self.assertFalse(self.validator.is_valid(self.event(None, status)))

    def test_creation_rejects_artificial_previous_status(self):
        for old in ["CREATED", "NONE", "", " "]:
            with self.subTest(old=old):
                self.assertFalse(self.validator.is_valid(self.event(old)))

    def test_transitions_reject_blank_previous_status(self):
        for old in ["", " ", "\t"]:
            self.assertFalse(self.validator.is_valid(self.event(old, "PICKED_UP")))

    def test_status_fields_are_required(self):
        for field in ["oldStatus", "newStatus"]:
            event = self.event()
            del event[field]
            self.assertFalse(self.validator.is_valid(event))

    def test_wrong_status_types_are_rejected(self):
        for field in ["oldStatus", "newStatus"]:
            for value in [42, [], {}]:
                event = self.event()
                event[field] = value
                self.assertFalse(self.validator.is_valid(event))

    def test_v1_is_not_silently_relaxed(self):
        schema = json.loads((CONTRACTS / "parcel.status-changed.v1.schema.json").read_text())
        self.assertFalse(Draft202012Validator(schema).is_valid(self.event()))

if __name__ == "__main__":
    unittest.main()
