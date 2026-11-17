# R3 - Permission and State

## Part 1 Test plan

Records: `test-plan.md` (`eb7843d`; changes `589243c`) and `test-map.csv`. Inputs are caller (owner, other student, owner of a different booking) and state (ACTIVE, CANCELLED, COMPLETED). The key cases test the check order: a non-owner gets DENIED even on an inactive booking or one starting soon, and an owner gets INACTIVE even when an inactive booking starts soon (not TOO_LATE). Our decision table rows cover non-owners and inactive states, and R4's cover owner + ACTIVE. Both required sequences are planned here. Changed after inspection: the notification should equal b1 in the CANCELLED state, not just have b1's ID.

## Part 2 Java tests

`R3PermissionAndStateTest.java` (`75bbbb6`), 12 cases: `nonOwnerIsDeniedWhateverTheStateOrTime`, `ownerGetsInactiveForCancelledOrCompletedBooking`, `ownerOfADifferentBookingIsDenied`, `nonOwnerThenOwner`, `cancelThenCancelAgain`. Rejections use `assertRejected` (status, storage unchanged including an unrelated booking, no notifications). Sequences check the saved booking and the full notification record. Each test makes its own fixture with the fixed clock. A status-only check would miss a non-owner's booking being saved as CANCELLED while DENIED is returned; the starter test `otherStudentIsDenied` misses exactly this, and our storage assertion catches it. Result: 65 run, 0 failed.

## Part 3 Coverage

Gap G3 (extra): starter (`2999086`) never ran line 48 (`return INACTIVE`), line 47 was 1/2 branches. Now 2/2 (`75bbbb6`). With R3 and R4, `BookingService` is at 31/31 lines and 20/20 branches (`evidence/runs/r3-coverage`). Limitation: one CANCELLED and one ACTIVE owner case give full branch coverage on line 47 but miss the time check running before the state check.

## Part 4 Mutation testing

All 7 PIT mutants on lines 44-48 and 55-57 killed (trial run, `mutations.csv`). We added 8 manual mutants, all killed. Reordering the checks is caught only by the non-owner + inactive and inactive + starts-soon cases, and notifying with the old ACTIVE booking only by the full notification comparison, neither of which coverage shows.

## Part 5 Bug repair

The supplied bug breaks R3/R5: a non-owner gets DENIED but the booking is saved as CANCELLED. On the faulty version 5 R3 cases fail an assertion, e.g. `nonOwnerIsDeniedWhateverTheStateOrTime[1]` on the storage check. The repair branch and bug record are still to do.

## Part 6 Extension

Not applicable to R3.
