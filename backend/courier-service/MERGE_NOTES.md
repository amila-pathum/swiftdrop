# Courier integration after merging develop

Reviewed 2026-11-17 against incoming develop commit `6d77380`.

## Conflict resolution and scope

The five conflicted Dispatch files and Parcel's `HttpPricingClient` use the
incoming develop versions. Earlier Dispatch compatibility overrides/test and
Parcel RDS overrides were also removed from this branch so both service trees
match develop exactly. Their new numeric IDs, migrations, API controllers and
pricing response compatibility are retained.

New implementation changes are inside `backend/courier-service`. Other incoming
files and the existing Courier frontend work remain intact. Tracked Python
`__pycache__` files were removed from the index; `demo/.gitignore` already excludes
them. No credentials or generated account files are added.

## Current authority and compatibility

- DISPATCHER assigns through Dispatch Service; Merchant assignment endpoints in
  Courier have been removed.
- Dispatch's `HttpDispatchDataClient` can call Courier's numeric `/profiles`
  directory using the dispatcher's token. A numeric `/profiles/{id}` endpoint
  is available without changing Dispatch's current list-based implementation.
- Courier consumes current `parcel.assigned` string-encoded numeric profile and
  Identity IDs and verifies they belong to the same Courier profile. Internal
  UUID references and old UUID-only fixture messages remain supported.
- Courier records status/evidence/history and publishes its own transactional
  outbox events. Existing Flyway migrations are preserved, including V5 already
  deployed during the earlier Merchant-authority assumption.
- Dispatch cancellation, downstream status synchronization and detailed parcel
  snapshots still require contracts from the owning services; see [API.md](API.md).

## Keeping later merges manageable

Finish this merge with its existing two parents; do not replace it with copied
service files or a squash that loses the develop merge ancestry. The conflict
resolutions and Courier adaptations are staged for review; no commit or push is
performed automatically.

Before the next PR, fetch the team's target branch, merge its latest state on
this feature branch and resolve/test there. Use the repository's chosen target
(`develop` or `main`) rather than assuming they are interchangeable. Keep future
business changes in Courier and coordinate shared contracts with their owners.
Add new migrations instead of editing applied migrations. Keep generated files,
environment credentials and `target/` out of commits.

Useful checks from the repository root:

```bash
git diff --name-only --diff-filter=U
git diff --cached --check
git diff MERGE_HEAD -- backend/dispatch-service backend/parcel-service
git diff --cached -- backend/courier-service
```

The first three checks should produce no output for this prepared merge.
`MERGE_HEAD` exists only until the merge commit is created. Git cannot guarantee
that future changes by different developers will never conflict.
