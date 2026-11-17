# R5 - Saved Data and Notifications

## Part 1 Test planning

R5 focuses on repository changes and notification requests after creating or cancelling bookings. The test plan covers successful and rejected requests, saved booking states, and unchanged unrelated bookings. The R5 cases are recorded in test-map.csv.

The two required cancellation sequences are covered under R3. R5 adds checks for the saved data and notification side effects of individual requests.

## Part 2 Automated tests

R5SavedDataAndNotificationsTest.java contains 5 test methods and 7 executed cases. All 7 passed with no failures or errors.

The tests check complete saved bookings and notification requests, not just returned statuses. For example, a cancellation could return CANCELLED without saving the new state or requesting a notification.

Each test uses a separate TestFixtures instance and the supplied fixed clock.

## Part 3 Coverage

The final PIT run reported 31/31 covered lines (100%) in BookingService. These are PIT line-coverage figures, not JaCoCo results.

The full team suite includes R1-R5. The combined coverage cannot be attributed to R5 alone.

## Part 4 Mutation testing

PIT 1.17.4 generated 20 mutants, with 20 killed (100%), 0 surviving and 0 uncovered. Test strength was 100%.

Three R5 mutants were analyzed in mutations.csv:

- Line 55: removing repository.save during cancellation was killed by successfulCancellationSavesAndNotifies.
- Line 56: removing notifier.cancellationRequested was killed by successfulCancellationSavesAndNotifies.
- Line 27: replacing the invalid-time CreateResult with null was killed by invalidCreationLeavesRepositoryUnchanged.

The repository and notification assertions detect errors that a return-status-only test could miss.

## Part 5 Faulty version

No separate R5 faulty-version investigation was performed. The group's Part 5 investigation is documented separately.

## Part 6 Extension

No separate R5 extension was implemented. The group's selected Part 6 extension is documented separately.