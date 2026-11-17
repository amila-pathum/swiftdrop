# Assigned deliveries: service and event audit

**Current authority correction:** Merchant assigns through Courier APIs; Courier
saves its own state and publishes Courier events. See [API.md](API.md) and the V5
Courier migration. The Dispatcher discussions and handoff proposals below are
historical analysis of the earlier assumption, not the current role contract.
Merchant code is unchanged. Verified merchant ownership must be imported into
Courier rather than accepted from an untrusted assignment request.

Reviewed against the repository on 2026-11-16. The initial audit changed no other
services; the subsequently authorized implementation changes Courier and the
targeted Dispatch assignment compatibility/event handoff described below.

Implementation update: Courier now consumes `parcel.assigned`, `parcel.status-changed`
and optional `parcel.delivery-snapshot.v1` messages into its own database. Courier
profiles have an explicit stable Dispatch UUID. With the user's subsequent approval,
Dispatch's RDS numeric-assignment compatibility was fixed, and its assignment event
now includes an optional `parcel` snapshot with addresses, recipient contacts, weight
and size obtained from Parcel's existing authorized API. No Identity or Parcel source
change is needed for this flow. The initial findings below describe the earlier gaps;
the runnable RDS demo is documented in `demo/README.md`.

Screenshot update: the Courier detail page now follows the supplied recipient,
pickup/drop-off, parcel, timeline, history and bottom-action layout. Courier V4
stores optional contacts/contents/dimensions and status history. The marked RDS
fixtures supply these through new RabbitMQ snapshots; ordinary parcels keep
missing fields explicitly unavailable until their owners publish those fields.

## Data required by the existing courier screens

The two supplied screenshots show the assigned-delivery list and a delivery detail page. The requirements below map those screenshots to the current backend and frontend. This is a requirements audit, not an implementation or a change to another team's event contract.

| Data | Owner / current source | Available in current RabbitMQ events? |
| --- | --- | --- |
| Parcel UUID and tracking number | Parcel; Dispatch repeats them when assigning | Yes: `parcel.assigned` and `parcel.status-changed` |
| Assigned courier UUID | Dispatch uses Courier's legacy UUID directory | Yes: `parcel.assigned.courierId` |
| Previous courier UUID | Dispatch reassignment | Yes: optional `previousCourierId` |
| Logged-in user ID | Identity numeric `User.id`, JWT subject | No identity lifecycle event publisher exists |
| Courier profile ID and user link | Courier numeric `courier_profiles.id` / `user_id` | Local, but no link to the legacy UUID directory exists |
| Assignment ID, active/state, version, attempt number | Dispatch assignment lifecycle | Not in the assignment event; its `eventId` is not the assignment ID |
| Assignment time and dispatcher | Dispatch | Yes: `occurredAt`, `assignedBy` |
| Scheduled delivery date | Needs an agreed scheduling owner/contract | No; assignment time is not a scheduled delivery date |
| Current parcel status | Parcel | Yes: `parcel.status-changed.newStatus` and `occurredAt` |
| Pickup address and delivery address | Parcel `ParcelDetailsResponse` | No |
| Recipient name and telephone | Parcel `ParcelDetailsResponse` | No |
| Weight, size and parcel type | Parcel `ParcelDetailsResponse` | No |
| Delivery charge | Parcel stores the accepted charge; Pricing calculates quotes | No; charge must not be interpreted as cash-on-delivery amount |
| Pickup contact name and telephone | Not stored on Parcel; Identity has account name/phone, but the pickup contact is not necessarily the account holder | No; capture a parcel-specific contact snapshot |
| Contents, for example Clothing | Not stored on Parcel; its `parcelType` means STANDARD/EXPRESS, not contents | No |
| Length, width and height in cm | Absent from Parcel; Merchant's `ParcelDetailsRequest` accepts these values but they are not exposed in Parcel's current model/events | No |
| Timeline/history location, attempt number and actor role | Parcel history has status/time/actor user ID/note; Courier checkpoints have location/attempt for its own submissions | Not in current status events; neither source alone supplies the full screenshot history |
| Checkpoints, proof photos and failed-attempt reasons | Courier's own evidence tables/private storage | Local after confirmed submissions |

Keep the existing browser response shape: `{ assignment: {...}, parcel: {...} }` inside `{ content, page, size, totalElements, totalPages }`. The frontend deduplicates by `assignment.id` and requires `parcel.id`. It uses `assignment.active` plus `parcel.status` for active/history grouping, and `assignment.deliveryDate` for the Today list.

## Exact screenshot field requirements

### Screenshot 1: assigned-delivery list

| Visible element | Required data / behavior |
| --- | --- |
| Tracking heading, e.g. SD10480015 | `parcel.id` for navigation and `parcel.trackingNumber` for display |
| Picked Up / Out for Delivery badge | Canonical `parcel.status` from Parcel, refreshed by status events |
| Scheduled 14 Nov 2026 | Explicit `assignment.deliveryDate` (`YYYY-MM-DD`); never substitute `assignedAt` |
| 2.5 kg | `parcel.weight`, in kilograms |
| Small | `parcel.size`, mapped from SMALL/MEDIUM/LARGE |
| Deliver to address | `parcel.deliveryAddress`; compact rendering must not discard the full address needed by the detail page |
| Next: Mark In Transit | Derive the label from confirmed status and current assignment ownership; Parcel remains authoritative for transitions |
| Open Delivery | Navigate using parcel UUID, then independently check ownership on the backend |
| Active / Completed / All and counts | Counts across all of this courier's assignments, not just the current page; use the same classification rules as the lists |
| Only parcels assigned to you | Resolve JWT numeric user ID to Courier profile and its explicit Dispatch UUID link; never accept a browser-selected courier ID for access control |

The prototype controls and the link to another courier's delivery are demonstration controls, not production data requirements. The unauthorized-delivery case belongs in an ownership test.

### Screenshot 2: delivery details

| Visible section | Required data / current gap |
| --- | --- |
| Header and status badge | Tracking number and confirmed current status |
| Deliver to / Call | Recipient name, full delivery address including postcode when provided, and recipient phone. Parcel stores a combined address string; it has no separate postal-code field |
| Pickup | Pickup contact name, pickup address and pickup phone. Parcel stores only pickup address and sender user ID, so the contact fields require an explicit snapshot contract |
| Drop-off | Recipient name, delivery address and recipient phone; use the same snapshot as Deliver to |
| Contents: Clothing | New contents description; do not map STANDARD/EXPRESS `parcelType` to this label |
| Weight: 2.5 kg | Existing Parcel weight, with units fixed to kg |
| Size: 25 x 20 x 10 cm (Small) | Length/width/height in cm plus existing size category. Do not derive dimensions from the category |
| Scheduled | Explicit delivery date, currently absent from the producing services |
| Timeline: Created, Picked Up, etc. | Confirmed status-history entries with event time and location. Future steps are UI placeholders without invented times or locations |
| History: Attempt 1 / by Customer or Courier | Stable history ID, status, changed time, actual location description, attempt number, actor user ID and actor role as recorded at the action |
| Mark In Transit | A status-change command with submission ID, authorized actor and current assignment identity/version; show confirmation only after Parcel commits acceptance |

Render instants in Asia/Colombo and scheduled dates as date-only values. A planned pickup address is not evidence of the actual checkpoint location. A note is not a structured location field, and customer ID on a status event is not the user who performed that transition.

The supplied screenshots do not require COD amount, pricing breakdown, ETA, a live map or continuous GPS. Those are outside this requirements baseline.

## What each service contributes

- **Identity** owns numeric user IDs, COURIER role and account status. Registration alone does not provision `courier_profiles`. Identity currently exposes account APIs but publishes no RabbitMQ account events. Never infer identity ownership from display names or demo courier numbering.
- **Courier** owns authenticated courier profiles, its legacy UUID courier directory, availability, checkpoints and proof storage. These two courier representations currently have no explicit identity mapping.
- **Dispatch** owns assignment, reassignment and cancellation. It reads the legacy UUID directory through `GET /api/couriers` and `GET /api/couriers/{uuid}`. It publishes assignment events after commit, but cancellation publishes no event.
- **Parcel** owns recipient contacts, addresses, physical parcel details, accepted charge and canonical status. Its outbox publishes creation/status events, but those payloads omit the display details.
- **Merchant** owns merchant information and order references. Its parcel views read Parcel data; they are not an additional authoritative address source or a RabbitMQ snapshot source.
- **Pricing** returns quote amount, currency and breakdown. Use Parcel's accepted charge for an existing parcel rather than calculating a new price when a courier opens a card.
- **Notification** consumes status events and exposes tracking/notifications. Its public tracking payload intentionally omits personal details; it cannot fill the missing addresses/contact fields.
- **API Gateway** already routes `/api/couriers/**` to Courier. No gateway change is needed for the current delivery endpoints.

## Initial integration failures (before implementation)

1. `HttpCourierIntegrations.dispatchClient` requests `/api/dispatch/couriers/{numericId}/assignments`. Dispatch implements `/api/dispatch/couriers/{uuid}/parcels`, restricted to ADMIN/DISPATCHER, with a different response. Changing only the path would not fix identity, authorization or response incompatibility.
2. The courier UI authenticates through a numeric Identity user and a numeric courier profile. Dispatch assigns a UUID legacy courier. There is no trusted join between them. Casting one identifier to another or matching names would expose assignments to the wrong account.
3. Courier currently has no AMQP dependency, queues, consumers, inbox deduplication or local assignment/parcel projection.
4. `ParcelAssignedEvent` has `eventId`, `eventType`, `occurredAt`, `parcelId`, `trackingNumber`, `courierId`, `previousCourierId` and `assignedBy`. It has no delivery address, recipient, schedule, assignment ID, assignment version or attempt number.
5. `ParcelStatusChangedEvent` has event metadata, parcel/tracking IDs, merchant/customer/courier references and old/new status. It cannot populate a full delivery card or details view.
6. `DispatchService.cancelAssignment` changes only Dispatch's database. A Courier consumer cannot learn about a cancellation from the existing event stream. Reassignment does publish an event.
7. Dispatch publishes assignments directly after commit, without a durable outbox. A broker failure can leave an assignment committed but its event unpublished. A new Courier queue also cannot recover historical messages that were published before it existed.
8. Dispatch's assignment does not update Parcel's numeric `assignedCourierId`. Displaying the assignment therefore does not by itself authorize a Parcel status update. Parcel currently has no RabbitMQ status-command consumer; a Courier publisher alone cannot complete that integration.

Relevant sources: `HttpCourierIntegrations.java`, `CourierDeliveryService.java`, `CourierProfile.java`, `Courier.java` in Courier; `DispatchController.java`, `DispatchService.java`, `ParcelAssignedEvent.java`, `DispatchEventPublisher.java` in Dispatch; `ParcelDetailsResponse.java`, `ParcelStatusChangedEvent.java`, `ParcelOutboxPublisher.java` in Parcel; shared `contracts/events`.

## RabbitMQ implementation boundary

The following work is possible entirely inside Courier:

- Declare Courier-owned durable queues on the existing durable `swiftdrop.events` topic exchange, bound to `parcel.assigned` and `parcel.status-changed`. Never consume another service's queue.
- Persist a local assignment/status read model in `courier_db`, with event-ID deduplication in the same transaction as projection updates.
- Handle duplicate, delayed and out-of-order events, assignment-before-parcel events and reassignment; configure bounded retries and a Courier dead-letter queue.
- Add an explicit, ADMIN-only association between the UUID used by Dispatch and an existing numeric courier profile. New profiles can receive a stable dispatch UUID exposed through the existing directory. Existing demo UUIDs must be linked deliberately, not guessed.
- Return available tracking/assignment/status data from Courier's existing list/detail APIs without HTTP fan-out. Keep missing address/contact/schedule fields null and document incompleteness. Do not fabricate a scheduled delivery date or a Dispatch assignment ID from event metadata.
- Test consumption, persistence, duplicate delivery, event ordering, ownership, reassignment, filters and pagination using disposable PostgreSQL and RabbitMQ containers, without touching RDS.

Full screenshot-equivalent behavior additionally needs publisher contracts that do not exist yet. They must be agreed with the owning teams; this task does not authorize changing those services:

| Required owner contract | Necessary fields / behavior |
| --- | --- |
| Dispatch assignment lifecycle event | Real `assignmentId`, `parcelId`, UUID `courierId`, stable numeric user/profile mapping or a Courier-owned UUID link, `state`, monotonic version, attempt number, assigned time and optional scheduled date; publish cancellation and reassignment as well as initial assignment |
| Parcel delivery snapshot event | `parcelId`, `trackingNumber`, pickup contact name/phone and address, recipient name/phone and delivery address, contents description, weight in kg, dimensions in cm, size category, status and monotonic snapshot version; publish creation and relevant edits |
| Recovery/replay | Durable Dispatch publication and a defined broker-based snapshot/replay path for pre-existing assignments and parcels; adding a subscriber does not backfill old data |
| Status commands, if included in scope | Parcel-owned RabbitMQ command consumer with assignment authorization, idempotency and correlated accepted/rejected result events; Courier must not report a published command as confirmed delivery |
| Identity lifecycle, if included in scope | Identity-owned account approval/status events for provisioning/activation without the existing synchronous Identity calls |

The field names above are requirements to agree, not events currently published by the application. A full delivery UI cannot be made accurate from today's limited event payloads by changing only a consumer.

## Proposed RabbitMQ handoff for the service owners

All names in this section are proposals, not existing routes or approved shared schemas. Existing `parcel.assigned` and `parcel.status-changed` publishers must remain compatible with their current consumers. Agree versioning before adding a replacement event, and avoid applying both old and replacement events as separate business actions.

| Producer | Proposed routing key | Required payload |
| --- | --- | --- |
| Dispatch | `dispatch.assignment-changed.v1` | `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `parcelId`, real `assignmentId`, UUID `courierId`, `previousCourierId`, `state`, parcel-scoped monotonic `assignmentVersion`, `attemptNo`, `assignedAt`, `assignedBy`, nullable explicit `deliveryDate` |
| Parcel | `parcel.delivery-snapshot.v1` | Event envelope, `parcelId`, `trackingNumber`, monotonic `snapshotVersion`, `pickupContactName`, `pickupContactPhone`, `pickupAddress`, `receiverName`, `receiverPhone`, `deliveryAddress`, `contentsDescription`, `weightKg`, nullable `lengthCm`/`widthCm`/`heightCm`, `size`, `status`, `statusVersion` |
| Parcel | `parcel.delivery-status-changed.v1` | Event envelope, `parcelId`, `trackingNumber`, stable `historyId`, monotonic `statusVersion`, `oldStatus`, `newStatus`, `changedAt`, `actorUserId`, `actorRole`, nullable `assignmentId`/`assignmentVersion`, `attemptNo`, nullable actual `locationDescription`, optional coordinates, nullable `submissionId`, optional `note` |

Each row uses UUID `eventId` and UTC ISO-8601 instants. ID names must distinguish numeric Identity `userId`, numeric Courier `profileId`, and Dispatch's UUID `courierId`. Assignment state and parcel status are separate: cancelling an assignment must not falsely cancel the parcel. Reassignment closes the previous assignment and creates a new one; increasing assignment version must work even when the assignment ID changes. Attempt number represents a delivery attempt, not a count of messages or routine courier reassignments.

For creation history, `oldStatus` and `assignmentId` may be null. Location may be null when no actual location was captured. Do not manufacture a creation location from the customer's account address. Missing dimensions, contents, schedule or pickup contact must remain explicitly unavailable for old records rather than being filled with screenshot examples.

Courier would declare its own durable queues, for example `courier.assignment-changed.v1`, `courier.delivery-snapshot.v1` and `courier.delivery-status-changed.v1`, bound to these keys on `swiftdrop.events`. All read APIs would query Courier's local projections; opening a page would not synchronously request data from another service.

### Status button: separate command/result contract

The screenshot includes a write action as well as read data. It cannot be implemented by publishing a status notification that pretends the transition already happened.

- Proposed command route: `parcel.delivery-status-update.requested.v1`, on a separately agreed command exchange. Required data: stable `submissionId`, `parcelId`, requested status, authenticated actor user ID, assignment ID/version, attempt number and captured evidence references/metadata. Do not put the end user's bearer token in an event.
- Parcel must validate the trusted command source, canonical courier authorization, current assignment and allowed transition, then atomically save status/history and an outbox result. A producer-supplied actor ID by itself is not authorization. Its assignment projection or ownership contract must first be reconciled with Dispatch.
- Proposed result: `parcel.delivery-status-update.resolved.v1`, carrying `submissionId`, `parcelId`, ACCEPTED/REJECTED, an explicit rejection code if rejected, authoritative status/version and history ID/time if accepted. Valid rejected commands also need a result even though no status-changed event is emitted.
- Courier persists evidence and an outgoing command in one local transaction, returns PENDING, and confirms or rejects using the correlated result. Repeated commands with the same submission ID must not add history or duplicate a physical delivery attempt.

### Recovery and consistency requirements

1. Publishers need durable outboxes; consumers need inbox event-ID deduplication committed with their projection changes. Acknowledge only after the consumer transaction commits.
2. Preserve status history even when an older event arrives later, while preventing it from replacing a newer current status. Use separate version sequences for assignment, snapshot and status, not cross-service wall-clock comparisons.
3. Store an assignment even if its parcel snapshot has not arrived. Present missing details explicitly. Do not drop assignment/status events because profile linking or another event is delayed.
4. Supply a broker-based initial snapshot/history replay for assignments and parcels that predate Courier's queues, with a checkpoint/version strategy to reconcile concurrent live events. The UI cannot honestly show complete History or counts until backfill is complete.
5. Receive cancellation and reassignment so the former courier loses actionable access. Define historical access separately from active access; reassigning a parcel must not expose the new courier's later proof/contact updates to the previous courier by accident.
6. Restrict personal-detail queues to authorized consumers. Configure Courier retries and dead-letter handling without changing or reading another service's queues.

## Acceptance checks for the screenshot behavior

- An assigned parcel appears for the explicitly linked courier after event consumption, including its real tracking number, status, address, weight, size and schedule when available.
- The detail page shows the same recipient/address snapshot as the card, the separate pickup contact, actual contents/dimensions and all confirmed history available through replay/events.
- Another courier cannot read the delivery by guessing its parcel ID. An unlinked UUID cannot be claimed by display-name matching.
- Reassignment/cancellation removes actionable access from the previous courier; late or duplicate events cannot restore it.
- List counts match the full filtered dataset; pagination, refresh and event redelivery cannot duplicate cards. Active/Completed/All must have documented handling for FAILED, CANCELLED and superseded assignments; the existing frontend treats DELIVERED and FAILED as Completed.
- Picked Up offers Mark In Transit. The button becomes confirmed only after the canonical Parcel result; rejected, pending and uncertain updates remain distinct.
- UTC timestamps display in Asia/Colombo. Missing source data remains unavailable; no scheduled date, location, contact, contents or dimensions are guessed.

The read path and explicit UUID linking described above are now implemented. Pickup
contact snapshots, contents, exact dimensions, a real scheduling source, complete
external history, cancellation events and RabbitMQ status-command/result handling
remain separate handoffs. The assigned-delivery UI reads existing data on page load;
reopen/refresh the page to see a newly received assignment.
