# R1 - Valid Times

## Part 1 Test plan

Records: `test-plan.md` (`ed271e3`, before reading the code; changes `8a7bc5b`) and `test-map.csv`. Boundaries are strict around now = `at(0)`: start `at(-1)`/`at(0)` invalid, `at(1)` valid; end at start-1/start invalid, start+1 valid. Invalid times overlapping an ACTIVE booking must give INVALID_TIME, not CONFLICT. Expected: INVALID_TIME, null ID, nothing saved; or CREATED with one ACTIVE booking; no notification. Added after inspection: a rejected request must not use up an ID.

## Part 2 Java tests

`R1ValidTimesTest.java` (`4df80ab`), 21 cases: `invalidTimesAreRejectedAndNothingIsSaved`, `validTimesCreateOneActiveBooking`, `invalidTimeIsReportedBeforeConflict`, `invalidRequestLeavesAllExistingBookingsUnchanged`, `rejectedRequestDoesNotAffectNextValidRequest`. Each test asserts status, ID, saved bookings and notifications, with a fresh fixture and fixed clock. A status-only check would miss a booking that gets saved and then INVALID_TIME returned; our storage assertion catches it. Result: 24 run, 0 failed.

## Part 3 Coverage

Gap G1: starter (`2999086`) never ran line 27 (`return INVALID_TIME`), line 26 was 2/4 branches. Now 4/4 (`4df80ab`). Line 26 outcomes: start check true by `invalidTimesAreRejectedAndNothingIsSaved` past/now cases, false by every future start; end check true by its end-before/zero-length cases, false by `validTimesCreateOneActiveBooking`. JaCoCo agrees. Overall lines 71%→90.3%, branches 35%→70% (`evidence/runs/starter-coverage`, `r1-coverage`). Limitation: `at(-60)` and `at(180)` alone give 4/4 branches but miss a `start == now` bug.

## Part 4 Mutation testing

All 3 PIT mutants on lines 26-27 killed by status assertions (trial run, `mutations.csv`). PIT has no boundary mutant for `isAfter()`, so we added 5 manual mutants, all killed. The start == now mutant is killed only by the `at(0)` cases, which coverage alone doesn't show.

## Part 5 Bug repair

Not applicable to R1. The supplied bug is in `cancel`.

## Part 6 Extension

Not applicable to R1.
