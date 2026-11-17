# Part 6 - Extension (Option B: failed save)

## Java files and tests

- `src/test/java/ca/ucalgary/seng300/booking/FailingSaveRepository.java`: test double. It works like the in-memory repository until `failSaves()` is called, then every `save` throws `RepositoryException` before anything is stored. Seeding happens before `failSaves()`, so setup never fails.
- `src/test/java/ca/ucalgary/seng300/booking/FailedSaveTest.java`: 2 tests. `TestFixtures` always uses the normal repository, so we build `BookingService` ourselves with the same fixed clock and recording notifier.
  - `failedSaveDuringValidCancellation`: the owner cancels an ACTIVE booking 240 minutes ahead (otherwise valid). We check `assertThrows(RepositoryException.class, ...)`, exactly one save attempt, `repository.all()` unchanged (b1 still ACTIVE, b2 untouched), and `notifier.requests()` empty.
  - `sameCancellationSucceedsWhenSaveWorks`: control. The same setup without `failSaves()` gives CANCELLED, saved, and one notification.

## Results

`.\mvnw.cmd clean test`: 74 run, 0 failed (72 core + 2 extension). We also tried two changes to `cancel` by hand and restored the code afterwards:

| Change | 72 core tests | `FailedSaveTest` |
| --- | --- | --- |
| Notify before saving | all pass | fails (notification sent for a cancellation that was never saved) |
| Catch and ignore the save exception | all pass | fails (exception doesn't reach the caller) |

## Why an always-successful replacement misses this

If `save` never fails, the order of save and notify, and what happens to the exception, can't change any result. Both changes above behave exactly like the correct code. Only a repository that can fail shows the difference.

## What our replacement can't prove

It only fails cleanly before any data changes, as `API.md` describes. It can't show what happens with a real database that fails halfway through a save, times out, or has two requests at once.

## Learning record

We used the README and `API.md` sections on Option B, and the JUnit 5.11.4 User Guide section on assertions for `assertThrows`.

Our first idea was to reuse `TestFixtures`, but it always creates its own in-memory repository, so we couldn't swap in a failing one. We changed to building `BookingService` ourselves with the same fixed clock and recording notifier. The README says the failure can't happen during setup, so the repository only starts failing after `failSaves()` is called, and we seed the bookings through `save` before that. We also added a counter so the test proves the failure came from the cancellation's own save.

To check the test was actually useful, we changed `cancel` to notify before saving. Our first try made no difference because the file had Windows line endings and the edit never applied. After fixing that, all 72 core tests still passed and only `FailedSaveTest` failed.

What we learned is that a fake that always succeeds can hide bugs in error handling and in the order things happen. To test those, the test double has to be able to fail on purpose. The control test is just as important, because it shows the failing case only fails because of the save.
