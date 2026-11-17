package ca.ucalgary.seng300.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Tests for R4 (cancellation time).
 *
 * R4: an owner can cancel an ACTIVE booking if there are at least 120 minutes
 * left before it starts. Less than 120 minutes gives TOO_LATE, while 120 or
 * more gives CANCELLED.
 *
 * TestFixtures uses a fixed clock, so the times passed to seed() are minutes
 * from now. The 119/120/121 cases check both sides of the cutoff and the
 * exact boundary.
 *
 * Every test makes its own TestFixtures so nothing is shared between tests.
 */
@DisplayName("R4 - Cancellation time")
class R4CancellationTimeTest {
    private static final String ROOM = "R204";
    private static final String OWNER = "student-a";

    // Checks cancellations at different times before the booking starts.
    // The 119/120/121 cases are the important boundaries. In particular,
    // 120 must be allowed, so changing < 120 to <= 120 would fail this test.
    @ParameterizedTest(name = "{0} minutes remaining: {1}")
    @CsvSource({
        "0, TOO_LATE",
        "1, TOO_LATE",
        "119, TOO_LATE",
        "120, CANCELLED",
        "121, CANCELLED",
        "240, CANCELLED"
    })
    void cancellationRespectsTimeBoundary(long minutesRemaining, CancelStatus expectedStatus) {
        TestFixtures f = new TestFixtures();
        Booking original = f.seed("b1", ROOM, OWNER, minutesRemaining,
                minutesRemaining + 60, BookingState.ACTIVE);

        CancelStatus actual = f.service.cancel(original.id(), OWNER);

        assertEquals(expectedStatus, actual);

        // A late cancellation should only return TOO_LATE. The booking must
        // still be ACTIVE and no notification should be requested.
        if (expectedStatus == CancelStatus.TOO_LATE) {
            assertEquals(original, f.repository.find("b1"), "late cancellation must not change stored booking");
            assertEquals(List.of(original), f.repository.all(), "late cancellation must not affect repository");
            assertTrue(f.notifier.requests().isEmpty(), "late cancellation must not notify");
        } else {
            // A successful cancellation should save the new state and request
            // exactly one notification. It shouldn't create another booking.
            Booking cancelled = original.withState(BookingState.CANCELLED);
            assertEquals(cancelled, f.repository.find("b1"), "successful cancellation must be saved");
            assertEquals(List.of(cancelled), f.repository.all(), "successful cancellation must not add bookings");
            assertEquals(List.of(cancelled), f.notifier.requests(), "successful cancellation must notify once");
        }
    }

    // Cancelling b1 at exactly 120 minutes should not affect b2, even though
    // both bookings are saved in the same repository.
    @Test
    @DisplayName("cancellation at 120 minutes leaves unrelated bookings unchanged")
    void successfulCancellationDoesNotChangeOtherBookings() {
        TestFixtures f = new TestFixtures();
        Booking target = f.seed("b1", ROOM, OWNER, 120, 180, BookingState.ACTIVE);
        Booking other = f.seed("b2", "R205", "student-b", 300, 360, BookingState.ACTIVE);

        assertEquals(CancelStatus.CANCELLED, f.service.cancel(target.id(), OWNER));

        // Only b1 should change state. b2 stays ACTIVE and only b1 is notified.
        assertEquals(target.withState(BookingState.CANCELLED), f.repository.find("b1"));
        assertEquals(other, f.repository.find("b2"));
        assertEquals(2, f.repository.all().size());
        assertEquals(List.of(target.withState(BookingState.CANCELLED)), f.notifier.requests());
    }

    // A request made 119 minutes before the start must be rejected without
    // changing either booking or requesting a notification.
    @Test
    @DisplayName("cancelling too late leaves unrelated bookings unchanged")
    void lateCancellationDoesNotChangeOtherBookings() {
        TestFixtures f = new TestFixtures();
        Booking target = f.seed("b1", ROOM, OWNER, 119, 179, BookingState.ACTIVE);
        Booking other = f.seed("b2", "R205", "student-b", 300, 360, BookingState.ACTIVE);

        assertEquals(CancelStatus.TOO_LATE, f.service.cancel(target.id(), OWNER));

        // Comparing the full list checks that both bookings are unchanged.
        assertEquals(List.of(target, other), f.repository.all());
        assertTrue(f.notifier.requests().isEmpty());
    }
}
