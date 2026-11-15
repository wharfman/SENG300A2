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


## Cancellation decision table

### R1 - Valid Times

Doesn't apply. R1 is only about creating bookings.

| Owner | State | Time remaining | Expected result |
| --- | --- | --- | --- |

## Two action sequences

### R1 - Valid Times

Doesn't apply. Both sequences are about cancelling (R3–R5).

## Changes after code inspection

### R1 - Valid Times

**Added: invalid requests shouldn't use up an ID.** 
In `create`, `nextId` is only called after the time check passes, so a rejected request shouldn't consume an ID. We added a check for this to the optional sequence: after an INVALID_TIME request, the next valid create in the same fixture should get `created-1`. R1 doesn't state this directly, so we're treating it as a sanity check rather than a rule test.

**Plan Changes:**
- Added the `created-1` ID check after an invalid request in the optional sequence.


