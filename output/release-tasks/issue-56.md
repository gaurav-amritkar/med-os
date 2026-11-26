<!-- medos-release-audit: rooms -->
## Outcome and priority

**P1 · Suggested accountable role: Backend/frontend admin lead**. Release tracker: #219.

The audit saw new room-creation changes; the original GET-only claim must be revalidated. Accepting a persistence entity is not a safe create contract.

Source: [production readiness review](https://github.com/gaurav-amritkar/med-os/blob/043832b4e1a2fb63e2805da05323077592f799bb/docs/reviews/2026-11-08-production-readiness-review.md), **§7 settings; R09**. This is an audit-derived task, not a fresh proof that the defect remains on current main. First reproduce or establish the missing evidence on the chosen candidate; if already fixed, attach the fixing commit and passing verification instead of reimplementing it. Original findings include source inspection and synthetic browser checks; they do not claim a live production incident.

## Implementation / investigation steps

1. Inspect current room-creation implementation before adding endpoints; replace direct entity binding with a validated request DTO.
2. Derive tenant and server-managed ID/occupancy from trusted context; reject negative rates/capacity and invalid enum/input values.
3. Provide authorized room/ward setup or explicitly exclude IPD; normalize ward naming without inventing a mandatory broad redesign.
4. Define occupied-room edit/retirement behavior, duplicate-number scope and room/bed capacity semantics.
5. Test admin UI setup without database edits and preserve native keyboard actions and neutral occupancy colors.

## Dependencies and coordination

- #220 — Freeze pilot scope and enforce excluded modules

Dependencies concerning optional modules apply when those modules are enabled. Any exclusion must be explicit in #219 and enforced in UI and API; it is not an informal waiver. P2 AI work is post-pilot unless separately approved into scope.

## Acceptance checks

- [ ] Client-supplied ID/tenant/occupied fields cannot overwrite or move existing records.
- [ ] Only authorized tenant admins create/change inventory; invalid capacity/rate and occupied retirement fail.
- [ ] Configured rooms are usable by admission/discharge and concurrent allocation remains safe.

## Evidence required to close

- [ ] Link PR/commit and the exact tested SHA, plus relevant automated checks or repeatable operational evidence.
- [ ] Add the task-specific negative, permission and failure-path results above; integration fixes must use actual API contracts and relevant real services.
- [ ] For UI work, attach synthetic-data screenshots and browser evidence at affected widths/states; for data/config changes, record upgrade/recovery implications.
- [ ] Document remaining limitations and obtain the named functional reviewer (clinical/cashier/security/operator as applicable). Use redacted or synthetic evidence only.

Do not close on a unit-test count alone. Never modify an applied migration; use a new version and update its manifest when schema changes are necessary. Assign an actual owner before work starts; the role above is not a GitHub assignee.

## Historical issue context

The execution plan above supersedes conflicting historical implementation/status assumptions below. This text is retained for traceability; it is not confirmation of current behavior. In particular, do not implement old migration numbers, plaintext clinical audit logging or obsolete missing-feature claims.

<details>
<summary>Previous issue description</summary>

### Summary

Rooms cannot be created through the API — the room endpoints are GET-only. Bed inventory has to be inserted by hand into the database. "Ward" is not a concept: it is a free-text column, so `ICU`, `icu` and `I.C.U.` are three different wards.

### Evidence

**Rooms are read-only.** `AdmissionController` has exactly two room endpoints, both GET:
- `GET /rooms` (`AdmissionController.java:25`)
- `GET /rooms/available` (`:31`)

No POST, PUT or DELETE. `AdmissionService` only reads rooms (`:134-140`), plus flipping `occupied` on admit and discharge (`:65-66`, `:100-101`). `RoomRepository` inherits `save`, but no service method calls it to create.

**The entity is rich enough to seed already** (`Room.java:31-53`): `roomNumber` (unique), `ward`, `roomType`, `dailyRate`, `capacity`, `occupied`, `floor`, `notes`. Table at `V1__initial_schema.sql:180-194`.

**Ward is a string.** `ward VARCHAR(64) NOT NULL` with no foreign key (`V1__initial_schema.sql:191`). The only lookup is `findByWard(String)` (`RoomRepository.java:18`), so matching is free-text and case-sensitive. `Room.roomType` (`general, semi_private, private_room, icu, nicu, operation`, `Room.java:66-68`) is the axis that actually carries semantics.

**No UI.** `Admissions.jsx` is titled "IPD / Wards" and renders a read-only occupancy grid with an admit modal. No add, edit, delete or grouping. `api/index.js:68-75` defines no room mutation methods.

### Requirements

- Room create / update / delete endpoints, guarded to `ADMIN` — nobody else should create beds. The RBAC test currently treats rooms as readable by every role (`RbacMatrixTest.java:326-332`); that stays true for reads.
- A `Ward` entity with a `wards` table, referenced by an FK from `rooms.ward`, in a new `V3__` migration. Backfill existing distinct ward strings.
- A manage-wards UI: list, create, rename, and see occupancy per ward.
- A manage-rooms UI: bulk add rooms, set rate/type/capacity, retire a room.
- Prevent deleting a ward that still has rooms, and deleting an occupied room.
- Decide whether `roomType` and `ward` overlap, and document the relationship — currently two independent axes with no constraint between them.

### Decisions needed

- **Wards as an entity, or a normalised lookup list?** A real `Ward` entity is the right call if wards will ever carry their own attributes — tariff, staffing, occupancy limits — which for an IPD module is likely. A lookup list is cheaper and prevents typos but cannot grow.
- Whether a general ward with mixed room types is allowed, or whether `roomType` should be derived from the ward.

### Verification

- An admin creates a ward and rooms in it through the API and the UI; the rooms appear in the occupancy grid.
- Duplicate `roomNumber` is rejected; duplicate ward names are rejected or merged.
- A non-admin user is refused room creation.
- Deleting a ward with rooms is refused; deleting an occupied room is refused.
- `ICU` and `icu` resolve to the same ward.

### Notes

- Includes a drive-by fix: `Admissions.jsx:78` keys its colour map on `private`, but the Java enum value is `private_room` (`Room.java:67`), so private rooms silently render with the default colour.
- Context: `docs/feature-releases.md` (item 6).


</details>
