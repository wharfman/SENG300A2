package ca.ucalgary.seng300.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Tests for R5 (saved data and notifications).
 *
 * R5 checks that successful requests save the correct booking and
 * rejected requests leave the repository unchanged. Only successful
 * cancellations should request a notification.
 *
 * R3 already tests the two required cancellation sequences, so these
 * tests focus on repository contents and notification side effects.
 */
@DisplayName("R5 - Saved data and notifications")
class R5SavedDataAndNotificationsTest {

    private static final String ROOM = "R204";
    private static final String OWNER = "student-a";

    // A successful cancellation should save the whole CANCELLED booking
    // and request exactly one notification containing that booking.
    @Test
    void successfulCancellationSavesAndNotifies() {
        TestFixtures f = new TestFixtures();
        Booking b1 = f.seed("b1", ROOM, OWNER, 240, 300, BookingState.ACTIVE);

        CancelStatus result = f.service.cancel("b1", OWNER);
        Booking cancelled = b1.withState(BookingState.CANCELLED);

        assertEquals(CancelStatus.CANCELLED, result);
        assertEquals(cancelled, f.repository.find("b1"));
        assertEquals(List.of(cancelled), f.repository.all());
        assertEquals(List.of(cancelled), f.notifier.requests());
    }

    // Rejected cancellations must not save anything or request notifications.
    // These cases reach different rejection paths in cancel().
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "non-owner, DENIED",
        "inactive, INACTIVE",
        "too-late, TOO_LATE"
    })
    void rejectedCancellationDoesNotChangeData(
            String caseName, CancelStatus expected) {
        TestFixtures f = new TestFixtures();

        Booking b1;
        String caller = OWNER;

        if (caseName.equals("non-owner")) {
            b1 = f.seed("b1", ROOM, OWNER, 240, 300, BookingState.ACTIVE);
            caller = "student-b";
        } else if (caseName.equals("inactive")) {
            b1 = f.seed("b1", ROOM, OWNER, 240, 300, BookingState.CANCELLED);
        } else {
            b1 = f.seed("b1", ROOM, OWNER, 119, 179, BookingState.ACTIVE);
        }

        Booking b2 = f.seed("b2", "R105", "student-c",
                360, 420, BookingState.ACTIVE);

        CancelStatus result = f.service.cancel("b1", caller);

        assertEquals(expected, result);
        assertEquals(List.of(b1, b2), f.repository.all());
        assertTrue(f.notifier.requests().isEmpty());
    }

    // Creating a booking should save one ACTIVE booking with the
    // requested fields, but should not send a cancellation notification.
    @Test
    void successfulCreationSavesWithoutNotification() {
        TestFixtures f = new TestFixtures();
        Booking existing = f.seed("b1", "R105", "student-b",
                120, 180, BookingState.ACTIVE);

        CreateResult result = f.service.create(
                ROOM, OWNER, TestFixtures.at(240), TestFixtures.at(300));

        Booking created = new Booking("created-1", ROOM, OWNER,
                TestFixtures.at(240), TestFixtures.at(300),
                BookingState.ACTIVE);

        assertEquals(CreateStatus.CREATED, result.status());
        assertEquals("created-1", result.bookingId());
        assertEquals(existing, f.repository.find("b1"));
        assertEquals(created, f.repository.find("created-1"));
        assertEquals(List.of(existing, created), f.repository.all());
        assertTrue(f.notifier.requests().isEmpty());
    }

    // Invalid creation must not add a booking or send a notification,
    // even when another booking already exists in the repository.
    @Test
    void invalidCreationLeavesRepositoryUnchanged() {
        TestFixtures f = new TestFixtures();
        Booking existing = f.seed("b1", ROOM, OWNER,
                120, 180, BookingState.ACTIVE);

        CreateResult result = f.service.create(
                ROOM, OWNER, TestFixtures.at(0), TestFixtures.at(60));

        assertEquals(CreateStatus.INVALID_TIME, result.status());
        assertNull(result.bookingId());
        assertEquals(List.of(existing), f.repository.all());
        assertTrue(f.notifier.requests().isEmpty());
    }

    // A conflicting create should not add another booking or change
    // the existing ACTIVE booking.
    @Test
    void conflictingCreationLeavesRepositoryUnchanged() {
        TestFixtures f = new TestFixtures();
        Booking existing = f.seed("b1", ROOM, OWNER,
                120, 240, BookingState.ACTIVE);

        CreateResult result = f.service.create(
                ROOM, "student-b", TestFixtures.at(180), TestFixtures.at(300));

        assertEquals(CreateStatus.CONFLICT, result.status());
        assertNull(result.bookingId());
        assertEquals(List.of(existing), f.repository.all());
        assertTrue(f.notifier.requests().isEmpty());
    }
}
