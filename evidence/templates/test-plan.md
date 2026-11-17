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

### R3 - Permission and State

R3 says ownership gets checked first, and anyone who isn't the owner gets `DENIED`. If it is the owner but the booking is already CANCELLED or COMPLETED, they get `INACTIVE`.

There are two inputs that matter here: who's calling (the owner or someone else) and what state the booking is in (ACTIVE, CANCELLED, COMPLETED). There aren't really number boundaries like R1 or R4, so the important cases are about the order of the checks:
- Since ownership is checked first, a non-owner should get DENIED no matter what state the booking is in or how much time is left. So a non-owner on a CANCELLED booking gets DENIED, not INACTIVE, and a non-owner on an ACTIVE booking that starts soon gets DENIED, not TOO_LATE.
- R4 only talks about cancelling ACTIVE bookings, so for an owner with an inactive booking we expect INACTIVE even if it starts in less than 120 minutes. It shouldn't turn into TOO_LATE.

We're using `student-a` as the owner and `student-b` as the other student. API.md says the caller ID comes from a trusted boundary, so we don't need to test things like fake logins.

| Group | Caller | b1 state | b1 time | Expected |
| --- | --- | --- | --- | --- |
| Non-owner, active, lots of time | student-b | ACTIVE | `at(240)`–`at(300)` | DENIED |
| Non-owner, active, starts soon | student-b | ACTIVE | `at(60)`–`at(120)` | DENIED (not TOO_LATE) |
| Non-owner, cancelled | student-b | CANCELLED | `at(240)`–`at(300)` | DENIED (not INACTIVE) |
| Non-owner, completed | student-b | COMPLETED | `at(-240)`–`at(-180)` | DENIED (not INACTIVE) |
| Owner of a different booking | student-c (owns b2) | ACTIVE | `at(240)`–`at(300)` | DENIED |
| Owner, cancelled | student-a | CANCELLED | `at(240)`–`at(300)` | INACTIVE |
| Owner, cancelled, starts soon | student-a | CANCELLED | `at(60)`–`at(120)` | INACTIVE (not TOO_LATE) |
| Owner, completed (in the past) | student-a | COMPLETED | `at(-240)`–`at(-180)` | INACTIVE |
| Owner, completed (future times) | student-a | COMPLETED | `at(240)`–`at(300)` | INACTIVE |

The "owner of a different booking" case is there to make sure the service checks the owner of *this* booking and not just whether the caller owns something.

We'll check more than just the status for DENIED and INACTIVE:
- b1 should be exactly the same in the repository as before, still in its old state (R5 says rejected requests change nothing).
- Any other bookings should be unchanged too.
- There should be no notification requests (R5).


## Cancellation decision table

## Cancellation decision table

### R1 - Valid Times

Doesn't apply. R1 is only about creating bookings.

### R2 - Room Conflicts

Doesn't apply. R2 is just about creating bookings.

### R3 - Permission and State

The R4 table below covers the owner with an ACTIVE booking. These rows cover the rest: non-owners and inactive bookings. Time remaining is minutes until the booking starts. "≥120" and "<120" are there to show time doesn't change the answer for these rows.

| Owner | State | Time remaining | Expected result |
| --- | --- | --- | --- |
| No | ACTIVE | ≥120 (240) | DENIED |
| No | ACTIVE | <120 (60) | DENIED |
| No | CANCELLED | ≥120 (240) | DENIED |
| No | CANCELLED | <120 (60) | DENIED |
| No | COMPLETED | already started (-240) | DENIED |
| Yes | CANCELLED | ≥120 (240) | INACTIVE |
| Yes | CANCELLED | <120 (60) | INACTIVE |
| Yes | COMPLETED | ≥120 (240) | INACTIVE |
| Yes | COMPLETED | already started (-240) | INACTIVE |
| Yes | ACTIVE | ≥120 / <120 | See R4 below |

Put together with R4, every combination of owner, state and time has an expected result. The order is: owner check first (DENIED), then state (INACTIVE), then time (TOO_LATE or CANCELLED).

### R4 - Cancellation Time

R4 allows the owner to cancel an ACTIVE booking if at least 120 minutes remain before its start. TestFixtures uses a fixed clock, so the times below are minutes from now.

| Owner | State | Time remaining | Expected result |
| --- | --- | --- | --- |
| Correct owner | ACTIVE | 0 | TOO_LATE |
| Correct owner | ACTIVE | 1 | TOO_LATE |
| Correct owner | ACTIVE | 119 | TOO_LATE |
| Correct owner | ACTIVE | 120 | CANCELLED |
| Correct owner | ACTIVE | 121 | CANCELLED |
| Correct owner | ACTIVE | 240 | CANCELLED |

The 119, 120 and 121-minute cases check both sides of the cutoff and the exact boundary.

For TOO_LATE, the booking must stay ACTIVE and no notification should be requested. For CANCELLED, the saved booking must change to CANCELLED and exactly one notification should be requested.

Two additional cases check that unrelated bookings are unchanged after a successful cancellation at 120 minutes and a rejected cancellation at 119 minutes.

## Two action sequences

### R1 - Valid Times

Doesn't apply. Both sequences are about cancelling (R3–R5).

### R2 - Room Conflicts

The two required sequences are both about cancelling, so they don't really fit R2. We did come up with an extra one that only uses `create`:
1. `create(R204, student-a, at(120), at(240))` should give CREATED (`created-1`).
2. `create(R204, student-b, at(180), at(300))` should give CONFLICT, with nothing saved and `created-1` left the same.
3. `create(R204, student-b, at(240), at(300))` should give CREATED with a new ID, since it starts right when the first booking ends.

We wanted this one because the other R2 tests only check against bookings we seed directly. This makes sure a booking made through the service also blocks later requests, and that the rejected request in the middle doesn't leave anything behind.

### R3 - Permission and State

Both required sequences involve R3, so we're planning both here. They also check R5 (saved data and notifications).

**Sequence 1: try as a non-owner, then as the owner.**
Start: b1 ACTIVE, owned by student-a, `at(240)`–`at(300)` (so there's more than 120 minutes left).
1. `cancel(b1, student-b)` should give DENIED. b1 is still ACTIVE in the repository and there are no notifications.
2. `cancel(b1, student-a)` should give CANCELLED. b1 is now saved as CANCELLED and there is exactly one notification request, for b1.

Why it matters: the failed attempt by someone else shouldn't change the booking or stop the real owner from cancelling it afterwards. It also makes sure the denied attempt doesn't sneak in a notification that would make the total 2.

**Sequence 2: cancel successfully, then cancel again.**
Start: b1 ACTIVE, owned by student-a, `at(240)`–`at(300)`.
1. `cancel(b1, student-a)` should give CANCELLED. b1 is saved as CANCELLED and there is exactly one notification request, for b1.
2. `cancel(b1, student-a)` again should give INACTIVE, because b1 is now CANCELLED (R3). b1 stays CANCELLED and there's still only one notification request in total (R5 says cancelling again adds no request).

Why it matters: this is the only way to check that a booking cancelled *through the service* (not seeded as CANCELLED) gets treated as inactive, and that a second cancel doesn't send a second notification.

For both sequences we'll only check *which* booking the notification is for (its ID), since the rules don't say what else the notification should contain.

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

### R3 - Permission and State

**Changed: check the whole booking that gets sent to the notifier.**
Before reading the code we only planned to check which booking the notification was for (its ID), since the rules don't say what the notification should contain. In `cancel`, the booking passed to `notifier.cancellationRequested` is the same one that gets saved, `booking.withState(BookingState.CANCELLED)`. So in both sequences the notification request should be exactly b1 with its state changed to CANCELLED, with the same ID, room, owner and times. Checking the whole record would also catch the service notifying with the old ACTIVE booking, which only checking the ID would miss.

**Plan Changes:**
- Both sequences now expect the notification request to equal b1 in the CANCELLED state, instead of just having b1's ID.


