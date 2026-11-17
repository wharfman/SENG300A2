# R4 - Cancellation Time

## Part 1 Test plan

R4 allows the owner to cancel an ACTIVE booking at least 120 minutes before it starts. With the fixed clock, at(119) should return TOO_LATE, while at(120) and at(121) should return CANCELLED. Also tested 0, 1 and 240 minutes. A late request must leave the booking unchanged with no notification; a successful request must save CANCELLED and request one notification. Other bookings should not change. These cases are recorded in test-map.csv.

## Part 2 Java tests

R4CancellationTimeTest.java, 8 cases: cancellationRespectsTimeBoundary, successfulCancellationDoesNotChangeOtherBookings, lateCancellationDoesNotChangeOtherBookings. The first test uses 6 parameterized inputs. Each uses a fresh fixture and fixed clock, and checks the result, saved booking and notifications. A status-only test could miss returning CANCELLED without actually saving the cancellation. Result: 8 run, 0 failed, 0 errors.

## Part 3 Coverage

The PIT run reported 30/31 lines (97%) for BookingService, using the configured test suite, not just R4. Line 51 (minutes < 120) was covered. This is not a JaCoCo before/after result; the group still needs to retain those reports for Part 3.

## Part 4 Mutation testing

PIT 1.17.4: 20 mutants, 18 killed (90%), 1 survived, 1 NO_COVERAGE; test strength 18/19 (95%). These are overall results not R4-only. Both mutants on line 51 were killed. The ConditionalsBoundaryMutator was killed by the at(120) test, which checks that cancellation is allowed at exactly 120 minutes. The RemoveConditionalMutator_ORDER_ELSE was killed by lateCancellationDoesNotChangeOtherBookings. The mutation removing repository.save (line 55) and the mutation removing the notification call (line 56) were also killed. The saved PIT XML confirms these results. The surviving mutant on line 47 and the uncovered mutant on line 48 concern R3's inactive-state check, rather than the R4 time boundary.

## Part 5 Bug repair

Not part of my R4 work. The supplied bug repair is a separate group task.

## Part 6 Extension

Not part of my R4 work.