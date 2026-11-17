package ca.ucalgary.seng300.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R4: an owner may cancel an ACTIVE booking at least 120 minutes before it starts. */
@DisplayName("R4 - Cancellation time")
class R4CancellationTimeTest {
    private static final String ROOM = "R204";
    private static final String OWNER = "student-a";

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
        if (expectedStatus == CancelStatus.TOO_LATE) {
            assertEquals(original, f.repository.find("b1"), "late cancellation must not change stored booking");
            assertEquals(List.of(original), f.repository.all(), "late cancellation must not affect repository");
            assertTrue(f.notifier.requests().isEmpty(), "late cancellation must not notify");
        } else {
            Booking cancelled = original.withState(BookingState.CANCELLED);
            assertEquals(cancelled, f.repository.find("b1"), "successful cancellation must be saved");
            assertEquals(List.of(cancelled), f.repository.all(), "successful cancellation must not add bookings");
            assertEquals(List.of(cancelled), f.notifier.requests(), "successful cancellation must notify once");
        }
    }

    @Test
    @DisplayName("cancellation at 120 minutes leaves unrelated bookings unchanged")
    void successfulCancellationDoesNotChangeOtherBookings() {
        TestFixtures f = new TestFixtures();
        Booking target = f.seed("b1", ROOM, OWNER, 120, 180, BookingState.ACTIVE);
        Booking other = f.seed("b2", "R205", "student-b", 300, 360, BookingState.ACTIVE);

        assertEquals(CancelStatus.CANCELLED, f.service.cancel(target.id(), OWNER));

        assertEquals(target.withState(BookingState.CANCELLED), f.repository.find("b1"));
        assertEquals(other, f.repository.find("b2"));
        assertEquals(2, f.repository.all().size());
        assertEquals(List.of(target.withState(BookingState.CANCELLED)), f.notifier.requests());
    }

    @Test
    @DisplayName("cancelling too late leaves unrelated bookings unchanged")
    void lateCancellationDoesNotChangeOtherBookings() {
        TestFixtures f = new TestFixtures();
        Booking target = f.seed("b1", ROOM, OWNER, 119, 179, BookingState.ACTIVE);
        Booking other = f.seed("b2", "R205", "student-b", 300, 360, BookingState.ACTIVE);

        assertEquals(CancelStatus.TOO_LATE, f.service.cancel(target.id(), OWNER));

        assertEquals(List.of(target, other), f.repository.all());
        assertTrue(f.notifier.requests().isEmpty());
    }
}
