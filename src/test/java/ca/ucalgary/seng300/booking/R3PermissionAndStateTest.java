package ca.ucalgary.seng300.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Tests for R3 (permission and state), plus the two required cancel sequences.
 *
 * R3: ownership is checked first, so anyone who isn't the owner gets DENIED.
 * If it is the owner but the booking is CANCELLED or COMPLETED, they get INACTIVE.
 *
 * The important cases are about the order of the checks. A non-owner should get
 * DENIED even when the booking is inactive or starts soon, and an owner with an
 * inactive booking should get INACTIVE even when it starts soon (not TOO_LATE).
 *
 * b1 is always owned by student-a. Every test makes its own TestFixtures.
 */
@DisplayName("R3 - Permission and state")
class R3PermissionAndStateTest {

    private static final String ROOM = "R204";
    private static final String OWNER = "student-a";
    private static final String OTHER_STUDENT = "student-b";

    // An unrelated booking in another room, so we can check a rejected cancel
    // doesn't touch anything else in the repository either.
    private static Booking seedUnrelated(TestFixtures f) {
        return f.seed("b2", "R105", "student-c", 300, 360, BookingState.ACTIVE);
    }

    // Everything that should be true when a cancel is rejected. We check storage and
    // notifications too, because a bug could return the right status but still save
    // the booking as CANCELLED, and a status-only test wouldn't notice.
    private static void assertRejected(TestFixtures f, CancelStatus expected, CancelStatus actual,
                                       List<Booking> bookingsBefore) {
        assertEquals(expected, actual);
        assertEquals(bookingsBefore, f.repository.all(), "rejected cancel shouldn't change anything (R5)");
        assertTrue(f.notifier.requests().isEmpty(), "rejected cancel shouldn't notify (R5)");
    }

    // Non-owners always get DENIED, whatever the state and however much time is left,
    // because ownership is checked first.
    @ParameterizedTest(name = "non-owner, b1 {0} at({1}) to at({2}) -> DENIED")
    @CsvSource({
        "ACTIVE,     240,  300",
        "ACTIVE,      60,  120",
        "CANCELLED,  240,  300",
        "CANCELLED,   60,  120",
        "COMPLETED, -240, -180"
    })
    void nonOwnerIsDeniedWhateverTheStateOrTime(BookingState state, long start, long end) {
        TestFixtures f = new TestFixtures();
        Booking b1 = f.seed("b1", ROOM, OWNER, start, end, state);
        Booking b2 = seedUnrelated(f);

        CancelStatus result = f.service.cancel("b1", OTHER_STUDENT);

        assertRejected(f, CancelStatus.DENIED, result, List.of(b1, b2));
    }

    // Owning *a* booking isn't enough, it has to be this one.
    @Test
    @DisplayName("owner of a different booking is still denied")
    void ownerOfADifferentBookingIsDenied() {
        TestFixtures f = new TestFixtures();
        Booking b1 = f.seed("b1", ROOM, OWNER, 240, 300, BookingState.ACTIVE);
        Booking b2 = seedUnrelated(f);

        CancelStatus result = f.service.cancel("b1", "student-c");

        assertRejected(f, CancelStatus.DENIED, result, List.of(b1, b2));
    }

    // Owner with a booking that's already cancelled or completed gets INACTIVE.
    // The "starts soon" CANCELLED case should still be INACTIVE and not TOO_LATE,
    // since R4 only talks about cancelling ACTIVE bookings.
    @ParameterizedTest(name = "owner, b1 {0} at({1}) to at({2}) -> INACTIVE")
    @CsvSource({
        "CANCELLED,  240,  300",
        "CANCELLED,   60,  120",
        "COMPLETED, -240, -180",
        "COMPLETED,  240,  300"
    })
    void ownerGetsInactiveForCancelledOrCompletedBooking(BookingState state, long start, long end) {
        TestFixtures f = new TestFixtures();
        Booking b1 = f.seed("b1", ROOM, OWNER, start, end, state);
        Booking b2 = seedUnrelated(f);

        CancelStatus result = f.service.cancel("b1", OWNER);

        assertRejected(f, CancelStatus.INACTIVE, result, List.of(b1, b2));
    }

    // Required sequence 1: someone else tries first, then the real owner cancels.
    // The denied attempt shouldn't change anything or add a notification.
    @Test
    @DisplayName("sequence: non-owner is denied, then the owner cancels")
    void nonOwnerThenOwner() {
        TestFixtures f = new TestFixtures();
        Booking b1 = f.seed("b1", ROOM, OWNER, 240, 300, BookingState.ACTIVE);
        Booking b2 = seedUnrelated(f);

        CancelStatus denied = f.service.cancel("b1", OTHER_STUDENT);
        assertRejected(f, CancelStatus.DENIED, denied, List.of(b1, b2));

        CancelStatus cancelled = f.service.cancel("b1", OWNER);
        Booking b1Cancelled = b1.withState(BookingState.CANCELLED);
        assertEquals(CancelStatus.CANCELLED, cancelled);
        assertEquals(b1Cancelled, f.repository.find("b1"), "b1 should now be saved as CANCELLED");
        assertEquals(b2, f.repository.find("b2"), "other bookings shouldn't change");
        // Checked against the whole record (changed after reading the code), not just the ID
        assertEquals(List.of(b1Cancelled), f.notifier.requests(), "exactly one notification, for the cancelled b1");
    }

    // Required sequence 2: cancel, then cancel again. The second one should be INACTIVE
    // because b1 is now CANCELLED, and it shouldn't add a second notification (R5).
    @Test
    @DisplayName("sequence: owner cancels, then cancels again")
    void cancelThenCancelAgain() {
        TestFixtures f = new TestFixtures();
        Booking b1 = f.seed("b1", ROOM, OWNER, 240, 300, BookingState.ACTIVE);
        Booking b2 = seedUnrelated(f);
        Booking b1Cancelled = b1.withState(BookingState.CANCELLED);

        CancelStatus first = f.service.cancel("b1", OWNER);
        assertEquals(CancelStatus.CANCELLED, first);
        assertEquals(b1Cancelled, f.repository.find("b1"));
        assertEquals(List.of(b1Cancelled), f.notifier.requests());

        CancelStatus second = f.service.cancel("b1", OWNER);
        assertEquals(CancelStatus.INACTIVE, second, "b1 was cancelled through the service, so it's inactive now");
        assertEquals(List.of(b1Cancelled, b2), f.repository.all(), "nothing should change the second time");
        assertEquals(List.of(b1Cancelled), f.notifier.requests(), "cancelling again shouldn't add a notification");
    }
}
