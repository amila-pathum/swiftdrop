# Member 7 – Dispatch & Reports: Completion Plan

Audit date: 2026-11-15, on branch `develop` at commit `1df3c0d`.
Source of requirements: *Member 7 – Dispatch & Reports: Work Guide* (Nov 2, 2026).

This plan is for the developers (human and AI agent) finishing Member 7's part.
Work through the packages **in order**. Each one lists the files to change, the steps,
and the acceptance checks a reviewer will run. Do not change another member's service
except where a package says so. Requests for other members are listed in section 5.

---

## 1. Current status

Legend: ✅ done · 🟡 partly done / works only with demo data · ❌ missing

### Backend (`backend/dispatch-service`)

| # | Guide task | Status | What exists / what is wrong |
|---|---|---|---|
| B1 | Assignment table | 🟡 | `dispatch_assignments` has parcel id, courier id, assigned by, assigned at, state (ACTIVE/COMPLETED/CANCELLED), and a unique index allowing one ACTIVE row per parcel. **But `courier_id` is a UUID**, while Parcel and Courier services identify couriers with numeric (`Long`) ids. See section 2, problem P1. |
| B2 | `GET /api/dispatch/parcels/unassigned` | 🟡 | Endpoint and filtering logic are done. By default (`DISPATCH_DATA_SOURCE=projection`) it reads **dispatch's own seeded demo table**, not parcel-service. The `http` mode calls the wrong parcel-service path and expects the wrong response shape (P2). |
| B3 | `GET /api/dispatch/couriers` with workload | 🟡 | Counting active assignments is done. Courier list comes from demo data by default; `http` mode calls courier-service's legacy UUID list (P1, P2). |
| B4 | `POST /api/dispatch/assignments` + rules | 🟡 | Rules for CREATED status and courier active/available are done and return clear errors. Problems: (a) a parcel with an active assignment is silently **reassigned** instead of rejected, which breaks the guide's rule. (b) **Parcel-service is never told about the assignment**, so the courier can never pick the parcel up (P3). |
| B5 | Reassign / cancel before pickup | 🟡 | Reassign happens through POST `/assignments`; cancel is `POST /assignments/{parcelId}/cancel`. Both check status CREATED. Neither updates parcel-service. |
| B6 | `GET /api/dispatch/deliveries` with status filter | 🟡 | Exists, but reads the demo projection table, so real parcels never show. |
| B7 | Event listener | 🟡 | Consumes `dispatch.parcel-status-changed`, stores each event in `dispatch_report_events` with `ON CONFLICT (event_id) DO NOTHING` (dedupe ✅), and marks the assignment COMPLETED on DELIVERED/FAILED ✅. Problems: an invalid event throws and is **requeued forever** (poison message). A CANCELLED parcel leaves its assignment ACTIVE. |
| B8 | `GET /api/dispatch/reports/summary` | ✅ / 🟡 | Built from stored events ✅ and returns deliveries per day, success rate, counts by status, and active per courier. Active-per-courier returns only ids, no names. The five **extra** report endpoints (`/status`, `/volume`, `/courier-performance`, `/failed-deliveries`, `/revenue-by-route`, `*.csv`) read the demo projection tables, which goes against "build them from your stored events". The frontend does not use them. |
| B9 | Roles DISPATCHER + ADMIN | ✅ | `@PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")` on both controllers. |
| — | Hand-off to Member 6 (courier's assigned parcels) | ❌ | Courier-service calls `GET /api/dispatch/couriers/{id}/assignments` with a **numeric** courier id and a **COURIER** token, and expects a specific JSON shape. Dispatch only has `/couriers/{uuid}/parcels`, which is DISPATCHER/ADMIN only and returns a different shape. Couriers therefore see no deliveries (P4). |
| — | API documentation | 🟡 | `contracts/openapi/dispatch-service.yaml` exists and matches the current paths. It must be updated with every change below. |

### Tests

| Guide test | Status |
|---|---|
| Assignment fails: parcel already assigned | 🟡 `rejectsDoubleAssignmentToSameCourier` covers only *same* courier |
| Assignment fails: parcel not CREATED | ✅ `rejectsAssignmentForNonCreatedParcel` |
| Assignment fails: courier not available | ✅ `rejectsAssignmentToUnavailableCourier`, `...InactiveCourier` |
| Only DISPATCHER/ADMIN can assign (others 403) | 🟡 only a GET endpoint is tested for 403; no test on `POST /assignments` |
| Reports give right numbers for sample events | ❌ |
| Same event id twice is counted once | ❌ |
| **Test suite is green** | ❌ `./mvnw test` → 39 run, **22 errors**: every Spring context test fails because the Rabbit listener tries to connect to a real broker (`Authentication failure`). |

### Frontend (`frontend/src/pages/dispatcher`, `services/dispatchService.js`)

| # | Guide task | Status | Notes |
|---|---|---|---|
| F1 | Dispatcher board: waiting parcels, courier dropdown with active count, Assign | ✅ | `DispatcherDashboardPage.jsx`. Unavailable couriers are disabled in the dropdown. |
| F2 | Reassign and cancel buttons | 🟡 | Buttons exist on the "all parcels" table, which is fed by the demo projection (`/api/dispatch/parcels`). Must move to an active-assignments list. |
| F3 | Ongoing deliveries: table, status filter, tracking search | ✅ UI / 🟡 data | `OngoingDeliveriesPage.jsx` is fine; backend data source is demo. |
| F4 | Workload view | ✅ | `CourierWorkloadPage.jsx` shows count + availability + a courier's active parcels. |
| F5 | Reports dashboard: date range, per-day bar chart, success/failure rate, active per courier | ✅ / 🟡 | `DispatchReportsPage.jsx`. "Active per courier" chart labels are raw ids, not names. |
| F6 | Menus only for DISPATCHER/ADMIN, using Member 1's route check | ✅ | `ProtectedRoute allowedRoles={['DISPATCHER','ADMIN']}` in `App.jsx`; links in `Sidebar.jsx` (`DISPATCH_LINKS`, including Reports). |
| — | Frontend tests for dispatcher pages | ❌ | None. (The 26 failing frontend tests are in `App.test.jsx` / `App.integration.test.jsx` and look for a "Customer dashboard" heading. They are **not** Member 7's, but do not add new failures.) |

### Done-when checklist from the guide

- [ ] A dispatcher can assign a courier to a waiting parcel: works only on demo data, and the courier can't act on it.
- [ ] Workload per courier is correct: correct maths, but on demo couriers.
- [ ] Reports show deliveries per day, success rate and active per courier: ✅ logic, needs real events flowing.
- [ ] Dashboard charts work with real data: blocked by P1–P4.

**Summary:** about 70% of the code is written. The remaining work is mostly **integration**: getting the real parcel and courier data in, using the right courier id, telling parcel-service and courier-service about assignments, plus the missing tests.

---

## 2. Blocking problems (why it doesn't work end-to-end yet)

**P1 – Courier id mismatch.** Dispatch uses UUID courier ids from its own demo `dispatch_couriers` table. The real system uses:
- courier-service profile id (`Long`, `CourierProfile.id`). Courier-service sends this as `{id}` when it asks dispatch for a courier's assignments.
- identity user id (`Long`, `CourierProfile.userId`). Parcel-service stores this as `assignedCourierId`, checks it when a courier updates a status, and puts it in the event's `courierId`.

**P2 – Wrong upstream calls in `HttpDispatchDataClient`.**
- `GET /api/parcels?status=CREATED` returns a Spring `Page<ParcelDetailsResponse>`, not a list. The correct endpoint is `GET /api/parcels/dispatch?status=CREATED`, which returns `List<DispatchParcelResponse>`. For a single parcel, use `GET /api/parcels/dispatch/{id}`.
- `GET /api/couriers` (no params) is courier-service's *legacy UUID* list. The real list is `GET /api/couriers/profiles?active=true&page=&size=`, a paged `Profile{id, userId, name, availability: AVAILABLE|BUSY|OFFLINE, active, ...}`.

**P3 – Parcel-service never learns the assignment.** Parcel-service lets a courier change status only if `parcel.assignedCourierId == courier's user id`. Dispatch must call `PATCH /api/parcels/{id}/courier` with body `{"courierUserId": <Long>}`. DISPATCHER/ADMIN are allowed to call it, so dispatch forwards the caller's token, which `HttpDispatchDataClient` already does.

**P4 – Courier-service hand-off endpoint missing.** Courier-service calls
`GET /api/dispatch/couriers/{courierProfileId}/assignments` with the **courier's own token** and expects:

```json
[{ "id": "uuid", "parcelId": "uuid", "courierId": 12, "attemptNo": 1,
   "active": true, "version": 0, "deliveryDate": "2026-11-15",
   "assignedAt": "2026-11-15T08:00:00Z" }]
```

It rejects the whole response if any item has a null `id`/`parcelId`, `courierId` ≠ the requested id, `attemptNo < 1`, or `version < 0`. (See `backend/courier-service/.../client/CourierIntegrations.java` and `CourierDeliveryService.assigned`.)

---

## 3. Decisions (already made; follow them)

1. **Courier identity:** each assignment stores **both** `courier_profile_id BIGINT` (courier-service `Profile.id`) and `courier_user_id BIGINT` (`Profile.userId`), plus a `courier_name` snapshot. All dispatch APIs expose `courierId` = **profile id** (Long). Use `courier_user_id` only when talking to parcel-service and when matching event `courierId`.
2. **No copies of other services' data.** `http` becomes the only data source. Delete the projection mode and the demo tables (`dispatch_parcels`, `dispatch_couriers`, `dispatch_status_history`). Dispatch's own data is `dispatch_assignments` and `dispatch_report_events` only.
3. **Assign and reassign are separate.** `POST /api/dispatch/assignments` rejects a parcel that already has an ACTIVE assignment (409). Reassign gets its own endpoint.
4. **Ongoing deliveries and parcel history come from stored events** (latest event per parcel), not from parcel-service's database, and not from a projection.
5. **Remove the extra report endpoints** (`/reports/status|volume|courier-performance|failed-deliveries|revenue-by-route` and `/reports/{report}.csv`) and the unused `getReport` / `exportReport` in `dispatchService.js`. Only `/reports/summary` is required. (If the team wants them later, rebuild them on `dispatch_report_events`.)

If a teammate disagrees with decision 1, settle it before WP2. Everything after WP2 depends on it.

---

## 4. Work packages

Rules for every package:
- Branch from `develop` as `feature/dispatch-<short-name>`, one PR per package (WP1 can go with WP2).
- `cd backend/dispatch-service && ./mvnw test` must pass, and `cd frontend && npx vitest run src/pages/dispatcher` must pass.
- When an endpoint changes, update `contracts/openapi/dispatch-service.yaml` **in the same PR**, using the same path, params and response shape as the code.
- Never edit an existing Flyway migration (`V1`–`V3`); add new ones (`V4__...`, `V5__...`).
- Keep the existing style: `NamedParameterJdbcTemplate` in `DispatchRepository`, records in `DispatchResponses`, `DispatchException(HttpStatus, message)` for errors (body `{"error": "..."}`).

### WP1 – Make the test suite green (small, do first)

**Why:** 22 context tests fail when RabbitMQ isn't reachable, so nobody can trust test results.

Steps:
1. Add `src/test/resources/application-test.properties` (or `@TestPropertySource` on the context tests) with
   `spring.rabbitmq.listener.simple.auto-startup=false` and `spring.rabbitmq.listener.direct.auto-startup=false`.
   Make sure `SecurityConfigTest`, `DispatchServiceApplicationTests` and `RdsProfileStartupTest` load it.
2. If a test still needs a `RabbitTemplate`, mock it with `@MockitoBean`.

Acceptance: with RabbitMQ **stopped**, `./mvnw test` → 0 failures, 0 errors (Docker-dependent tests may be skipped when Docker is off, but must pass when it is on).

### WP2 – Real upstream clients + courier identity (core fix for P1, P2)

Files: `client/HttpDispatchDataClient.java`, `client/DispatchDataClient.java`, delete `client/ProjectionDispatchDataClient.java`, `dto/response/DispatchResponses.java`, `application.properties`, new `db/migration/V4__courier_identity_and_drop_projection.sql`.

Steps:
1. **Migration V4:**
   - `dispatch_assignments`: add `courier_profile_id BIGINT`, `courier_user_id BIGINT`, `courier_name VARCHAR(100)`. Make the old `courier_id` / `previous_courier_id` UUID columns nullable, or delete the existing demo assignment rows and drop those columns (preferred: there is no production data). Then set `courier_profile_id` and `courier_user_id` `NOT NULL`. Add `previous_courier_profile_id BIGINT`.
   - Add index `(state, courier_profile_id)`.
   - `DROP TABLE dispatch_status_history, dispatch_parcels, dispatch_couriers` (drop dependent indexes and FKs first).
   - Keep `dispatch_report_events` as it is.
2. **Client DTOs** (inside the client, not exposed by the API):
   - `UpstreamParcel(UUID parcelId, String trackingNumber, String pickupArea, String dropoffArea, String pickupAddress, String deliveryAddress, String size, String status, Long courierUserId, BigDecimal charge, Instant createdAt, Instant updatedAt)`, matching `parcel-service/.../DispatchParcelResponse`. Ignore unknown fields.
   - `UpstreamCourier(Long id, Long userId, String name, String availability, boolean active)`, matching courier-service `CourierDtos.Profile`.
3. **`DispatchDataClient` interface:**
   - `List<UpstreamParcel> createdParcels()` → `GET {parcel}/api/parcels/dispatch?status=CREATED`
   - `Optional<UpstreamParcel> findParcel(UUID)` → `GET {parcel}/api/parcels/dispatch/{id}` (404 → empty)
   - `List<UpstreamCourier> couriers()` → `GET {courier}/api/couriers/profiles?active=true&page=N&size=100`, **looping over all pages**.
   - `Optional<UpstreamCourier> findCourier(Long profileId)` → filter `couriers()` until Member 6 ships a by-id endpoint (section 5).
   - `void assignParcelCourier(UUID parcelId, Long courierUserId)` → `PATCH {parcel}/api/parcels/{id}/courier` with body `{"courierUserId": n}`. Map 404 → 404 "Parcel not found", 409 → 409 with parcel-service's message, other errors → 503.
   - Remove `lockParcel` (the unique partial index `uq_dispatch_assignment_active_parcel` already prevents double active assignments).
4. Make all paths configurable as now (`swiftdrop.parcel.*`, `swiftdrop.courier.*`) with the new defaults. Remove `swiftdrop.dispatch.data-source` and the `@ConditionalOnProperty`.
5. Public `CourierWorkload` becomes `(Long courierId, Long userId, String name, boolean active, String availability, boolean available, long activeParcelCount)`, where `available = active && availability == "AVAILABLE"`.
6. `GET /api/dispatch/couriers` returns **all active couriers** (so the workload view shows BUSY/OFFLINE too). The assign dropdown already disables the unavailable ones.

Acceptance:
- No code reads `dispatch_parcels` / `dispatch_couriers` / `dispatch_status_history`; `grep -rn "dispatch_parcels\|dispatch_couriers\|dispatch_status_history" src/main/java` returns nothing.
- Unit test for `HttpDispatchDataClient` with `MockRestServiceServer`: correct URLs, bearer token forwarded, paging loop, 404 → empty, 5xx → 503.

### WP3 – Assign / reassign / cancel rules (fixes B4, B5, P3)

Files: `service/DispatchService.java`, `repository/DispatchRepository.java`, `controller/DispatchController.java`, `dto/request/*`.

Endpoints (final):

| Method & path | Body | Success | Errors |
|---|---|---|---|
| `POST /api/dispatch/assignments` | `{parcelId: uuid, courierId: long}` | 201 + `AssignmentResult` | 404 parcel not found · 409 "Parcel is not waiting for a courier" (status ≠ CREATED) · 409 "Parcel already has an active assignment" · 400 "Courier not found" · 409 "Courier must be active and AVAILABLE" · 503 upstream down |
| `POST /api/dispatch/assignments/{parcelId}/reassign` | `{courierId: long}` | 200 + `AssignmentResult` (with `previousCourierId`) | 404 · 409 "Parcel has no active assignment" · 409 "Courier can only be changed before pickup" · 409 same courier · courier errors as above |
| `POST /api/dispatch/assignments/{parcelId}/cancel` | – | 200 + `AssignmentResult` state CANCELLED | 409 no active assignment · 409 after pickup |
| `GET /api/dispatch/assignments?state=ACTIVE&courierId=&trackingNumber=&page=&pageSize=` | – | `PageResult<AssignmentRow>` | 400 bad params |

`AssignmentRow` = `assignmentId, parcelId, trackingNumber, courierId, courierName, assignedBy, assignedAt, state, parcelStatus` (parcelStatus = latest event status, otherwise `CREATED`).

Assign algorithm (inside one `@Transactional`):
1. Validate input, then `findParcel` → 404 if empty. Status must be `CREATED`.
2. `repository.activeAssignment(parcelId)` present → 409.
3. `findCourier(courierId)` → 400 if empty; it must be `active && availability == AVAILABLE`, otherwise 409.
4. Insert the ACTIVE row (catch `DataIntegrityViolationException` → 409 "already has an active assignment").
5. Call `dataClient.assignParcelCourier(parcelId, courier.userId())`. If it throws, the exception rolls back the insert.
6. Publish `ParcelAssignedEvent` (after commit, as now). Update `contracts/events/parcel.assigned.v1.schema.json` if the field types change (courier ids become numbers as strings).

Reassign: same checks, except step 2 requires an existing active row. Mark the old row CANCELLED (`cancelled_by`, `cancelled_at`), insert the new row, and call parcel-service with the new user id.

Cancel: mark the row CANCELLED. Parcel-service has **no un-assign endpoint yet** (section 5, request R1). Until R1 lands, leave the parcel-service value as it is and add a `// TODO(R1)` comment. When R1 lands, call it in the same transaction as the reassign call.

Also delete `GET /api/dispatch/parcels` and `GET /api/dispatch/parcels/{parcelId}` (projection-based), or re-implement `/parcels/{parcelId}` as: parcel from parcel-service + `history` from `dispatch_report_events` for that parcel + the assignment rows for that parcel. **Preferred: re-implement**, because the dashboard's details panel uses it.

Acceptance (unit tests in `DispatchServiceTest`, with the data client mocked):
- `assignFailsWhenParcelAlreadyHasActiveAssignment` (different courier) → 409
- `assignFailsWhenParcelNotCreated` → 409 (keep)
- `assignFailsWhenCourierBusyOrOffline` / `...Inactive` → 409
- `assignCallsParcelServiceWithCourierUserId`
- `assignRollsBackWhenParcelServiceFails` (repository test or verify no publish + exception)
- `reassignRequiresActiveAssignment`, `reassignRejectedAfterPickup`, `cancelRejectedAfterPickup`

### WP4 – Courier hand-off endpoint (fixes P4)

New controller `controller/CourierAssignmentsController.java`, so the class-level DISPATCHER/ADMIN rule on `DispatchController` does not apply to it:

`GET /api/dispatch/couriers/{courierId}/assignments`
- `@PreAuthorize("hasAnyRole('COURIER','DISPATCHER','ADMIN')")`
- A COURIER may read only their own rows: every row returned must have `courier_user_id == Long.valueOf(jwt.getSubject())`. If the path's profile id belongs to another user → 403. If there are no rows, return `[]`.
- Returns all assignments (ACTIVE, COMPLETED and CANCELLED-by-reassign for history) for that `courier_profile_id`, ordered by `assigned_at`.
- Shape per item: `id` (assignment_id), `parcelId`, `courierId` (profile id, Long), `attemptNo` (1-based position of this row among all rows for the same parcel, ordered by `assigned_at`; use `row_number()`), `active` (`state = 'ACTIVE'`), `version` (add `version BIGINT NOT NULL DEFAULT 0`; increment it **only** when dispatch itself cancels the row (cancel or reassign), **never** when an event marks it COMPLETED. Courier-service stores the version with each status submission and rejects a retry with "Assignment changed" if it differs, so bumping on COMPLETED would break a retried DELIVERED/FAILED submission), `deliveryDate` (`assigned_at::date` UTC), `assignedAt`.

Replace the old `/couriers/{id}/parcels` used by `CourierWorkloadPage`: either keep it (DISPATCHER/ADMIN, now with a Long id, ACTIVE only) or have the page use the new endpoint and filter `active`. Pick one, and keep the frontend and OpenAPI in sync.

Acceptance:
- MockMvc tests: COURIER reading own → 200; COURIER reading another courier's id → 403; DISPATCHER → 200; CUSTOMER → 403.
- JSON field names exactly as in P4. Courier-service's validation (`attemptNo >= 1`, `version >= 0`, matching `courierId`) holds.

### WP5 – Event listener hardening + ongoing deliveries from events (fixes B6, B7)

Files: `event/ParcelStatusChangedConsumer.java`, `service/DispatchService.java`, `repository/DispatchRepository.java`, `application.properties`.

Steps:
1. In the consumer: on JSON parse failure or `DispatchException` with 400, throw `AmqpRejectAndDontRequeueException`, so the message goes to the dead-letter exchange already configured on the queue (`swiftdrop.dlx`). Also set `spring.rabbitmq.listener.simple.default-requeue-rejected=false`. Log the event id and the reason (no personal data).
2. `applyStatusEvent` (repository): remove the updates to the dropped projection tables. Keep the insert into `dispatch_report_events` with `ON CONFLICT DO NOTHING`, and return early when 0 rows were inserted. Then:
   - `DELIVERED` or `FAILED` → ACTIVE assignment → `COMPLETED` (already done; do **not** change `version`, see WP4).
   - `CANCELLED` → ACTIVE assignment → `CANCELLED` with `cancelled_by = 'parcel-event'`.
3. `GET /api/dispatch/deliveries?status=&trackingNumber=&page=&pageSize=`: take the latest event per parcel (`DISTINCT ON (parcel_id) ... ORDER BY parcel_id, occurred_at DESC, event_id DESC`). By default, keep statuses `PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY`; an explicit `status` filter may also be `DELIVERED` or `FAILED`. Left-join `dispatch_assignments` on `parcel_id` (latest row) for `courierId` / `courierName`. Response row: `parcelId, trackingNumber, status, courierId, courierName, lastUpdatedAt`. Escape the tracking search the way it is done now.
4. Keep accepting `oldStatus = null` only for `CREATED` (already done).

Acceptance (Testcontainers Postgres repository test, `@Testcontainers(disabledWithoutDocker = true)` like `RdsProfileStartupTest`):
- `sameEventIdTwiceIsStoredOnce`: apply the same event twice → 1 row; the report counts it once.
- `deliveredEventCompletesActiveAssignment`, `cancelledEventCancelsActiveAssignment`.
- `deliveriesShowsLatestStatusPerParcel`, plus the status filter and the tracking search.
- Consumer unit test: a malformed message throws `AmqpRejectAndDontRequeueException`.

### WP6 – Reports cleanup + correctness tests (B8)

Files: `controller/DispatchReportsController.java`, `service/DispatchService.java`, `repository/DispatchRepository.java`, `service/ReportRange.java`.

Steps:
1. Delete the extra report endpoints and the CSV export, plus their service and repository methods and DTOs (`VolumeByPeriod`, `CourierPerformance`, `FailedDelivery`, `RouteRevenue` if no longer used).
2. `activeDeliveriesPerCourier` item becomes `{courierId (profile id, Long), courierName, count}`: match event `courier_id` (user id string) to `dispatch_assignments.courier_user_id`. "Active" = latest status for the parcel is `PICKED_UP | IN_TRANSIT | OUT_FOR_DELIVERY` as of the end of the range (current logic). Also include couriers whose ACTIVE assignment is still CREATED, and document whichever rule you choose in the OpenAPI description.
3. Keep `successRate` = DELIVERED / (DELIVERED + FAILED) × 100, rounded to 2 decimals, 0 when there are none. Add `failureRate` to the response, so the frontend doesn't compute it.
4. Keep the date validation in `ReportRange` (from ≤ to, at most 2 years).

Acceptance (Testcontainers repository/service test with a fixed sample set):
Insert this sample set and assert **exact** numbers for range 2026-11-01..2026-11-03:
- 11-01: parcel A DELIVERED, parcel B FAILED
- 11-02: parcel C DELIVERED, parcel D DELIVERED, parcel E PICKED_UP (courier 7)
- 11-03: parcel F OUT_FOR_DELIVERY (courier 7), parcel G IN_TRANSIT (courier 9)
- 10-31 (outside range): parcel H DELIVERED

Expected: deliveriesPerDay = `[{11-01, delivered 1, failed 1, total 2}, {11-02, 2, 0, 2}, {11-03, 0, 0, 0}]`; successRate = 75.00; failureRate = 25.00; countsByStatus DELIVERED 3, FAILED 1, PICKED_UP 1, OUT_FOR_DELIVERY 1, IN_TRANSIT 1; active per courier: courier 7 → 2, courier 9 → 1.

### WP7 – Security tests (guide: "only DISPATCHER and ADMIN can assign")

In `SecurityConfigTest` (or a new `DispatchControllerSecurityTest` with `@WebMvcTest` and the service mocked):
- `POST /api/dispatch/assignments` with CUSTOMER, MERCHANT, COURIER → **403**; DISPATCHER and ADMIN → not 403.
- Same for `/reassign`, `/cancel`, `GET /reports/summary`.
- No token → 401.

### WP8 – Frontend updates

Files: `src/services/dispatchService.js`, `src/pages/dispatcher/*.jsx`, plus new tests `src/pages/dispatcher/*.test.jsx`.

1. `dispatchService.js`: add `reassign(parcelId, courierId)` → `POST /assignments/{id}/reassign`, and `getAssignments(params)` → `GET /assignments`. Remove `getParcels`, `getReport`, `exportReport`. Courier ids are now numbers.
2. **Dispatcher board:** keep the waiting-parcels table + Assign. Replace the "all parcels" table with an **Active assignments** table (from `getAssignments({state:'ACTIVE'})`) with **Reassign** and **Cancel** buttons. Enable them only when `parcelStatus === 'CREATED'`; otherwise show "Picked up" and keep them disabled. Show the backend `error` message on failure. Refresh both tables and the courier list after every action.
3. Courier dropdown: `name · N active · Available/Busy/Offline/Inactive`; only `available` couriers are selectable (already done; adapt to the new fields).
4. **Ongoing deliveries:** adapt to the new row shape (`lastUpdatedAt`, `courierName`). The status filter offers PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED.
5. **Workload:** show availability text (AVAILABLE/BUSY/OFFLINE) and the active count; clicking a courier lists their active parcels.
6. **Reports:** label the active-per-courier bars with `courierName`, and use the `failureRate` from the API. Deliveries per day should show delivered and failed (two bars or a stacked bar per day); the current single "total" bar is acceptable if time is short.
7. Tests with mocked `dispatchService` (follow the pattern in `App.test.jsx`):
   - Board: renders waiting parcels; picking a courier and pressing Assign calls `assignCourier(parcelId, courierId)`; unavailable couriers are disabled; an API error is shown.
   - Active assignments: Reassign/Cancel call the right functions; buttons are disabled when the parcel is picked up.
   - Ongoing deliveries: the status filter and tracking search pass the right params.
   - Reports: renders success/failure %, one bar per day, courier names.

Acceptance: `npx vitest run src/pages/dispatcher` is green; `npm run lint` has no new errors; manual run (section 6) works.

### WP9 – Docs

- `contracts/openapi/dispatch-service.yaml`: the final paths, params, request/response schemas, error codes and roles for every endpoint above. Remove the deleted ones.
- `backend/dispatch-service/README.md`: data sources (parcel-service, courier-service APIs, RabbitMQ queue), env vars, how to run, the courier id rule (decision 1).
- `contracts/events/parcel.assigned.v1.schema.json`: courier id fields as used.

---

## 5. Requests to other members (send these now; WP3/WP4 depend on them)

| Id | To | Request | Needed by |
|---|---|---|---|
| R1 | Member 3 (parcel) | An un-assign call: `DELETE /api/parcels/{id}/courier` (DISPATCHER/ADMIN, only while CREATED), or allow `courierUserId: null` on the PATCH. | WP3 cancel |
| R2 | Member 3 (parcel) | `DispatchParcelResponse.from` sets `pickupArea`/`dropoffArea` to the full addresses, and the `Parcel` entity has no area/zone field. Optional: add one (e.g. the pricing zone) and return it. Dispatch filters work on addresses meanwhile. | WP2 filters (non-blocking) |
| R3 | Member 6 (courier) | `GET /api/couriers/profiles/{id}` (ADMIN/DISPATCHER), so dispatch doesn't page through the whole list to check one courier. | WP2 (non-blocking; fallback exists) |
| R4 | Member 6 (courier) | Path, profile id and COURIER token are already confirmed by courier-service code (`HttpCourierIntegrations`, `CourierDeliveryService`). Only confirm: `version` changes only when dispatch cancels/reassigns, never on COMPLETED. | WP4 |
| R5 | Member 10 (RabbitMQ) | Optional: a dedicated `dispatch.dlq` instead of reusing `notification.failed` as the dead-letter routing key. | WP5 (non-blocking) |
| R6 | Member 1 (gateway) | **Not needed.** The gateway routes `/api/dispatch/**` by path with no role filtering, and courier-service calls dispatch directly (`DISPATCH_SERVICE_URL`), not via the gateway. | – |

---

## 6. How the work will be reviewed

Tell me when a package (or all of them) is merged into a branch and I'll check it against this list:

1. **Static checks:** the package's acceptance items above; no reads of other services' DBs; no projection tables left; OpenAPI matches the controllers (every `@*Mapping` path, param and status code).
2. **Tests:** `./mvnw test` in `backend/dispatch-service` (with RabbitMQ off, and again with Docker on for Testcontainers), and `npx vitest run src/pages/dispatcher`. All guide-required tests exist and pass:
   - assignment fails: already assigned / not CREATED / courier not available
   - non-DISPATCHER/ADMIN gets 403 on assign
   - report numbers for the sample set in WP6
   - duplicate event id counted once
3. **End-to-end run** (`docker compose up`, then identity, parcel, courier, dispatch, gateway and the frontend):
   - As a customer, create a parcel → it appears on the dispatcher board.
   - As a dispatcher, assign it to an AVAILABLE courier → the courier's count goes up by 1; assigning again → 409 with a clear message; a BUSY courier can't be picked.
   - Reassign and then cancel before pickup work; after pickup both are refused.
   - As that courier, the delivery shows in the courier app; mark PICKED_UP → it appears in Ongoing deliveries; mark DELIVERED → the assignment is COMPLETED and the count goes down by 1.
   - Reports for today show 1 delivered, 100% success, and the per-day bar.
   - Re-publish the same event (RabbitMQ UI) → the numbers don't change. Publish a malformed message → it goes to the DLQ and the listener keeps running.
4. **Guide's done-when list** in section 1 is fully ticked.
