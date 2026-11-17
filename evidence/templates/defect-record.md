# One bug record

## Rule and setup

The supplied faulty patch (`faulty-version/buggy-version.patch`) breaks **R3**. R3 says a non-owner gets DENIED. On the faulty version a non-owner still gets DENIED, but the booking gets saved as CANCELLED anyway.

Everything is on the branch `investigate-bug`, which is never merged into the reference:
- Reference: `8a6d08b` (`main`)
- Faulty version: `7fd24a5` (only the supplied patch applied)
- Repaired version: `da4278a`

Detecting test: `R3PermissionAndStateTest.nonOwnerIsDeniedWhateverTheStateOrTime`, mainly case `[1]` (b1 ACTIVE, owned by student-a, `at(240)`–`at(300)`).

## Steps to reproduce

1. From the reference commit, `git switch -c investigate-bug`.
2. `git apply --check ..\faulty-version\buggy-version.patch`, then `git apply ..\faulty-version\buggy-version.patch`, then commit.
3. Run `.\mvnw.cmd clean test "-Dtest=R3PermissionAndStateTest#nonOwnerIsDeniedWhateverTheStateOrTime"`.

The test seeds b1 (ACTIVE, owned by student-a, starts in 240 minutes) and an unrelated booking b2, then calls `cancel("b1", "student-b")`.

## Expected and actual outcomes

| | Expected (R3/R5) | Actual on faulty version |
| --- | --- | --- |
| Response | DENIED | DENIED |
| b1 in storage | Still ACTIVE | **CANCELLED** |
| b2 in storage | Unchanged | Unchanged |
| Notifications | None | None |

The response is correct, so the starter test `otherStudentIsDenied` still passes on the faulty version. Our test catches it with the storage check in `assertRejected`, `assertEquals(bookingsBefore, f.repository.all())`, which fails with "rejected cancel shouldn't change anything (R5)".

## Cause and minimal fix

The patch adds `repository.save(booking.withState(BookingState.CANCELLED));` inside the non-owner branch of `cancel`, right before `return CancelStatus.DENIED;`. This means anyone can cancel someone else's booking, even though they're told DENIED.

Fix: delete that one line (commit `da4278a`, `evidence/bug/repair.patch`). After the fix `BookingService.java` is the same as the reference again.

## Same test on reference and buggy and repaired versions

Command: `.\mvnw.cmd clean test "-Dtest=R3PermissionAndStateTest#nonOwnerIsDeniedWhateverTheStateOrTime"`

| Version | Commit | Result | Evidence |
| --- | --- | --- | --- |
| Reference | `8a6d08b` | 5 run, 0 failed | `evidence/runs/bug-reference/` |
| Faulty | `7fd24a5` | 5 run, **3 failed** (`[1]`, `[2]`, `[5]`), all assertion failures | `evidence/runs/bug-faulty/` |
| Repaired | `da4278a` | 5 run, 0 failed | `evidence/runs/bug-repaired/` |

Cases `[3]` and `[4]` still pass on the faulty version because b1 is already CANCELLED in those, so saving it as CANCELLED again doesn't change anything.

## Full suite after repair

`.\mvnw.cmd clean test` on `da4278a`: 65 run, 0 failed, 0 errors (`evidence/runs/bug-repaired/full-suite-output.txt`).
On the faulty version `7fd24a5` the full suite had 5 failures, all in `R3PermissionAndStateTest` (`evidence/runs/bug-faulty/full-suite-output.txt`).

## Remaining risk

Rerunning the suite only checks the cases we wrote, and only against the in-memory repository, which never fails. It can't show that the repaired `cancel` works with a real database, for example that nothing gets half-saved if a save fails, or that two requests at the same time can't both get through. Concurrency and real databases are outside this assignment, so the passing suite only tells us the fix is right for one request at a time with in-memory storage.
