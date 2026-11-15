# R2 - Room Conflicts

## Part 1 Test plan

Records: `test-plan.md` (`ec46013`, written from the rule only, though after the R1 code inspection; changes `56e5503`) and `test-map.csv`. Overlap means sharing at least one minute, and touching is allowed both ways. Against b1 (R204, `at(120)`-`at(240)`) the boundaries are 1 min overlap / touching / 1 min gap at each end. Also exact, inside, covering, different room, same owner, CANCELLED/COMPLETED b1, and two-booking cases. Expected: CONFLICT, null ID, nothing saved; or CREATED with one ACTIVE booking and existing ones unchanged; no notification. Changed after inspection: a CONFLICT doesn't use up an ID, so CREATED cases expect exactly `created-1` and the sequence's last step `created-2`.

## Part 2 Java tests

`R2RoomConflictsTest.java` (`4a7e19c`), 21 cases: `overlappingRequestsInTheSameRoomAreConflicts`, `touchingSeparateOrOtherRoomRequestsAreCreated`, `inactiveBookingsDoNotConflict`, `sameOwnerOverlapIsStillAConflict`, `conflictWithSecondBookingIsFound`, `requestThatFitsExactlyInTheGapIsCreated`, `cancelledBookingDoesNotHideActiveOne`, `bookingMadeThroughServiceBlocksLaterRequests`. Each test asserts status, ID, saved bookings and notifications, with a fresh fixture and fixed clock. A status-only check would miss a booking that gets saved and then CONFLICT returned; our storage assertion catches it. Result: 45 run, 0 failed.

## Part 3 Coverage

Gap G2: starter (`2999086`) never ran the conflict loop body, so lines 30-34 were missed and lines 30-33 had 0/8 branches. Now all covered (`4a7e19c`). Overall lines 90.3%→93.5%, branches 70%→90% (`evidence/runs/r2-coverage`), meeting both targets. Remaining misses are INACTIVE and TOO_LATE (R3, R4). Limitation: one overlap plus one request well before and one well after b1 give full branch coverage but miss treating touching as a conflict.

## Part 4 Mutation testing

All 7 PIT mutants on lines 30-39 killed (trial run, `mutations.csv`). The save removal is only caught by the storage assertion. PIT has no boundary mutant for `isBefore()`, so we added 10 manual mutants, all killed. The touching mutants are killed only by the touching and gap-fit cases, which coverage alone doesn't show.

## Part 5 Bug repair

Not applicable to R2. The supplied bug is in `cancel`.

## Part 6 Extension

Not applicable to R2.
