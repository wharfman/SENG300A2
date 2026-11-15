# Initial test plan

Write this before inspecting method bodies.

## Input groups and boundaries

### R1 - Valid Times

R1 says a booking has to start after the current time and end after it starts. This check happens before conflicts are checked, and if the times are invalid we should get `INVALID_TIME` back with nothing saved.

We're treating "now" as the fixture's fixed clock time, `at(0)`. We read "after" as strictly after, so a booking starting exactly at `at(0)` or ending exactly when it starts should be rejected. Since all times are whole minutes, the closest values on either side of a boundary are one minute away, so we test -1, 0 and +1 around each one.


| Group | Start | End | Expected |
| --- | --- | --- | --- |
| Start in the past | `at(-60)` | `at(60)` | INVALID_TIME |
| Start 1 min before now (boundary) | `at(-1)` | `at(60)` | INVALID_TIME |
| Start exactly now (boundary) | `at(0)` | `at(60)` | INVALID_TIME |
| Start 1 min after now (boundary) | `at(1)` | `at(60)` | CREATED |
| End before start | `at(120)` | `at(60)` | INVALID_TIME |
| End 1 min before start (boundary) | `at(120)` | `at(119)` | INVALID_TIME |
| End equals start, zero length (boundary) | `at(120)` | `at(120)` | INVALID_TIME |
| End 1 min after start (boundary) | `at(120)` | `at(121)` | CREATED |
| Both invalid | `at(-10)` | `at(-20)` | INVALID_TIME |
| Typical valid booking | `at(180)` | `at(240)` | CREATED |

Since R1 has to be checked before conflicts, we also want a few cases where the times are invalid but the booking would also overlap an existing one. If the service checked conflicts first it would return CONFLICT, so these should all still give INVALID_TIME.

| Existing booking | Start | End | Expected |
| --- | --- | --- | --- |
| `b1` ACTIVE, room R1, `at(60)`–`at(180)` | `at(120)` | `at(90)` | INVALID_TIME |
| `b1` ACTIVE, room R1, `at(60)`–`at(180)` | `at(120)` | `at(120)` | INVALID_TIME |
| `b1` ACTIVE, room R1, `at(-30)`–`at(60)` | `at(-10)` | `at(30)` | INVALID_TIME |

For each of these we'll check more than just the returned status:
- The booking ID should be null when the result is INVALID_TIME (API.md).
- For INVALID_TIME, the repository should only contain whatever we seeded, with nothing added or changed.
- For CREATED, there should be exactly one new ACTIVE booking with the room, owner and times we asked for.
- The notifier should have no requests in any case, since R5 says creating a booking never sends one.

### R2 - Room Conflicts

R2 says two ACTIVE bookings in the same room can't overlap, and if they do we get `CONFLICT`. A booking can start right when another one ends. Different rooms don't conflict, and neither do bookings that aren't ACTIVE. If there's no conflict we get `CREATED` and one new ACTIVE booking gets saved.

We think "start exactly when another ends" should work both ways, so a new booking can start when an existing one ends or end when an existing one starts. In both cases they just touch and don't actually share any time. So for the boundaries we test touching, overlapping by 1 minute, and leaving a 1 minute gap, on both sides of the existing booking.

R2 doesn't mention the owner at all, so we're assuming you can't double book a room even if the other booking is your own.

For all of these there's one existing booking `b1` that's ACTIVE in room R204 from `at(120)` to `at(240)`. All the requests use valid times so R1 doesn't affect anything, and they're made by a different student unless we say otherwise.

| Group | Room | Start | End | Expected |
| --- | --- | --- | --- | --- |
| Exact same times | R204 | `at(120)` | `at(240)` | CONFLICT |
| Overlaps the start of b1 | R204 | `at(60)` | `at(180)` | CONFLICT |
| Overlaps the end of b1 | R204 | `at(180)` | `at(300)` | CONFLICT |
| Inside b1 | R204 | `at(150)` | `at(210)` | CONFLICT |
| Covers all of b1 | R204 | `at(60)` | `at(300)` | CONFLICT |
| Same start, ends earlier | R204 | `at(120)` | `at(180)` | CONFLICT |
| Same end, starts later | R204 | `at(180)` | `at(240)` | CONFLICT |
| 1 min overlap at the start of b1 (boundary) | R204 | `at(60)` | `at(121)` | CONFLICT |
| Touches the start of b1 (boundary) | R204 | `at(60)` | `at(120)` | CREATED |
| 1 min gap before b1 (boundary) | R204 | `at(60)` | `at(119)` | CREATED |
| 1 min overlap at the end of b1 (boundary) | R204 | `at(239)` | `at(300)` | CONFLICT |
| Starts exactly when b1 ends (boundary) | R204 | `at(240)` | `at(300)` | CREATED |
| 1 min gap after b1 (boundary) | R204 | `at(241)` | `at(300)` | CREATED |
| Same owner as b1, overlapping | R204 | `at(150)` | `at(210)` | CONFLICT |
| Different room, same times | R105 | `at(120)` | `at(240)` | CREATED |

Bookings that aren't active shouldn't block anything, so we'll make the same request (R204, `at(120)` to `at(240)`) with b1 in each state:

| b1 state | Expected |
| --- | --- |
| ACTIVE | CONFLICT |
| CANCELLED | CREATED |
| COMPLETED | CREATED |

We also want some cases with more than one booking already in the room, because the conflict might not be with the first one:

| Existing bookings (all R204) | Start | End | Expected | Why |
| --- | --- | --- | --- | --- |
| b1 ACTIVE `at(120)`–`at(240)`, b2 ACTIVE `at(360)`–`at(480)` | `at(400)` | `at(450)` | CONFLICT | Overlaps b2, not b1 |
| b1 ACTIVE `at(120)`–`at(240)`, b2 ACTIVE `at(360)`–`at(480)` | `at(240)` | `at(360)` | CREATED | Fits right in the gap and touches both |
| b1 CANCELLED `at(120)`–`at(240)`, b2 ACTIVE `at(120)`–`at(240)` | `at(150)` | `at(210)` | CONFLICT | The cancelled one shouldn't stop it from finding the active one |

Like R1, we'll check more than just the status for each case:
- For CONFLICT, the booking ID should be null (API.md), nothing should be added to the repository, and nothing that was already there should change. No notifications.
- For CREATED, the booking ID shouldn't be null. The repository should have everything we seeded (unchanged) plus one new ACTIVE booking with the room, owner and times we asked for. No notifications either, since R5 says creating never sends one.
- The existing bookings should never change because of a create, even the cancelled or completed ones (R5 says to leave other bookings unchanged).


## Cancellation decision table

### R1 - Valid Times

Doesn't apply. R1 is only about creating bookings.

### R2 - Room Conflicts

Doesn't apply. R2 is just about creating bookings.

| Owner | State | Time remaining | Expected result |
| --- | --- | --- | --- |


## Two action sequences

### R1 - Valid Times

Doesn't apply. Both sequences are about cancelling (R3–R5).

### R2 - Room Conflicts

The two required sequences are both about cancelling, so they don't really fit R2. We did come up with an extra one that only uses `create`:
1. `create(R204, student-a, at(120), at(240))` should give CREATED (`created-1`).
2. `create(R204, student-b, at(180), at(300))` should give CONFLICT, with nothing saved and `created-1` left the same.
3. `create(R204, student-b, at(240), at(300))` should give CREATED with a new ID, since it starts right when the first booking ends.

We wanted this one because the other R2 tests only check against bookings we seed directly. This makes sure a booking made through the service also blocks later requests, and that the rejected request in the middle doesn't leave anything behind.

## Changes after code inspection

### R1 - Valid Times

**Added: invalid requests shouldn't use up an ID.** 
In `create`, `nextId` is only called after the time check passes, so a rejected request shouldn't consume an ID. We added a check for this to the optional sequence: after an INVALID_TIME request, the next valid create in the same fixture should get `created-1`. R1 doesn't state this directly, so we're treating it as a sanity check rather than a rule test.

**Plan Changes:**
- Added the `created-1` ID check after an invalid request in the optional sequence.

### R2 - Room Conflicts

**Changed: a conflict shouldn't use up an ID either.**
Before reading the code we didn't know if a CONFLICT would use up an ID, since the rules don't say. That's why step 3 of our sequence only expected "a new ID" and the CREATED cases only expected the ID to not be null. In `create`, `nextId` only gets called after the conflict loop finishes without finding anything, so a rejected request can't use one up. So step 3 should get exactly `created-2`, and the CREATED cases should get exactly `created-1` since they're the first booking made in a fresh fixture.

**Plan Changes:**
- Sequence step 3 now expects `created-2` instead of just a new ID.
- CREATED cases now expect `created-1` instead of just a non-null ID.


