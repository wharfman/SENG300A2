package ca.ucalgary.seng300.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static ca.ucalgary.seng300.booking.TestFixtures.at;
import static org.junit.jupiter.api.Assertions.*;

/*
 * Tests for R1 (valid times).
 *
 * R1: a booking has to start after now and end after it starts. This is checked
 * before conflicts. Invalid times give INVALID_TIME and nothing gets saved.
 *
 * "Now" is the fixed clock in TestFixtures, so at(0) is now. We read "after" as
 * strictly after, so starting at at(0) or ending at the same time it starts is invalid.
 *
 * Every test makes its own TestFixtures so nothing is shared between tests.
 */
@DisplayName("R1 - Valid times")
class R1ValidTimesTest {

    private static final String ROOM = "R204";
    private static final String OWNER = "student-a";
    private static final String OTHER_STUDENT = "student-b";

    // Checks everything that should be true when a request is rejected for bad times.
    // We check more than the status because a bug could return INVALID_TIME but still
    // save the booking (or send a notification), and a status-only test wouldn't notice.
    private static void assertRejected(TestFixtures f, CreateResult result, List<Booking> bookingsBefore) {
        assertEquals(CreateStatus.INVALID_TIME, result.status());
        assertNull(result.bookingId(), "rejected requests shouldn't get a booking ID");
        assertEquals(bookingsBefore, f.repository.all(), "nothing should be saved or changed");
        assertTrue(f.notifier.requests().isEmpty(), "creating a booking should never notify (R5)");
    }

    // Invalid times, starting from an empty repository.
    // The -1/0/+1 boundary cases around now and around the start time are the important ones.
    @ParameterizedTest(name = "{0}: at({1}) to at({2})")
    @CsvSource({
        "start in the past,              -60,  60",
        "start 1 min before now,          -1,  60",
        "start exactly now,                0,  60",
        "end before start,               120,  60",
        "end 1 min before start,         120, 119",
        "end equals start (zero length), 120, 120",
        "both start and end invalid,     -10, -20",
        "whole booking in the past,      -60, -30",
        "start now and zero length,        0,   0",
        "end before now (start future),   10, -10"
    })
    void invalidTimesAreRejectedAndNothingIsSaved(String description, long start, long end) {
        TestFixtures f = new TestFixtures();

        CreateResult result = f.service.create(ROOM, OWNER, at(start), at(end));

        assertRejected(f, result, List.of());
    }

    // Valid times. The first new booking in a fresh fixture should always be "created-1".
    @ParameterizedTest(name = "{0}: at({1}) to at({2})")
    @CsvSource({
        "start 1 min after now,   1,  60",
        "end 1 min after start, 120, 121",
        "typical valid booking, 180, 240",
        "smallest valid booking (1 min after now and 1 min long), 1, 2"
    })
    void validTimesCreateOneActiveBooking(String description, long start, long end) {
        TestFixtures f = new TestFixtures();
        Booking expected = new Booking("created-1", ROOM, OWNER, at(start), at(end), BookingState.ACTIVE);

        CreateResult result = f.service.create(ROOM, OWNER, at(start), at(end));

        assertEquals(CreateStatus.CREATED, result.status());
        assertEquals("created-1", result.bookingId());
        // Comparing the whole record checks room, owner, times and ACTIVE state all at once
        assertEquals(List.of(expected), f.repository.all(), "should save exactly one booking with the requested details");
        // Make sure the returned ID actually points at the saved booking
        assertEquals(expected, f.repository.find(result.bookingId()));
        assertTrue(f.notifier.requests().isEmpty(), "creating a booking should never notify (R5)");
    }

    // R1 has to be checked before conflicts. Each request here has bad times AND overlaps
    // an active booking in the same room, so if conflicts were checked first we'd get CONFLICT.
    // (The last two seeds start in the past, which is why we use seed() and not create().)
    @ParameterizedTest(name = "{0}: overlaps b1 at({1})-at({2}), request at({3}) to at({4})")
    @CsvSource({
        "end before start,   60, 180, 120,  90",
        "zero length,        60, 180, 120, 120",
        "start in the past, -30,  60, -10,  30",
        "start exactly now, -30,  60,   0,  30"
    })
    void invalidTimeIsReportedBeforeConflict(String description, long seedStart, long seedEnd,
                                             long start, long end) {
        TestFixtures f = new TestFixtures();
        Booking existing = f.seed("b1", ROOM, OTHER_STUDENT, seedStart, seedEnd, BookingState.ACTIVE);

        CreateResult result = f.service.create(ROOM, OWNER, at(start), at(end));

        assertRejected(f, result, List.of(existing));
    }

    // "Save nothing" should also mean the bookings already there don't get touched,
    // including ones in other rooms and ones that aren't active.
    @Test
    @DisplayName("invalid request doesn't change any existing bookings")
    void invalidRequestLeavesAllExistingBookingsUnchanged() {
        TestFixtures f = new TestFixtures();
        Booking otherRoom = f.seed("b1", "R105", OTHER_STUDENT, 60, 180, BookingState.ACTIVE);
        Booking cancelled = f.seed("b2", ROOM, OTHER_STUDENT, 60, 180, BookingState.CANCELLED);
        Booking completed = f.seed("b3", ROOM, "student-c", -240, -180, BookingState.COMPLETED);

        CreateResult result = f.service.create(ROOM, OWNER, at(120), at(60));

        assertRejected(f, result, List.of(otherRoom, cancelled, completed));
    }

    // Not really an R1 test on its own. This shows the seed from the ordering tests
    // really does overlap, otherwise those tests could pass without testing anything.
    @Test
    @DisplayName("control: the ordering-test seed does give CONFLICT when the times are valid")
    void orderingSeedReallyConflictsWithValidTimes() {
        TestFixtures f = new TestFixtures();
        f.seed("b1", ROOM, OTHER_STUDENT, 60, 180, BookingState.ACTIVE);

        CreateResult result = f.service.create(ROOM, OWNER, at(90), at(120));

        assertEquals(CreateStatus.CONFLICT, result.status());
    }

    // Sequence we added after reading the code: a rejected request shouldn't leave anything
    // behind, and it shouldn't use up an ID, so the next valid booking still gets created-1.
    @Test
    @DisplayName("invalid create then valid create: the rejected one leaves nothing behind")
    void rejectedRequestDoesNotAffectNextValidRequest() {
        TestFixtures f = new TestFixtures();

        CreateResult rejected = f.service.create(ROOM, OWNER, at(120), at(120));
        assertRejected(f, rejected, List.of());

        CreateResult created = f.service.create(ROOM, OWNER, at(120), at(180));
        assertEquals(CreateStatus.CREATED, created.status());
        assertEquals("created-1", created.bookingId(), "the rejected request shouldn't have used up an ID");
        assertEquals(List.of(new Booking("created-1", ROOM, OWNER, at(120), at(180), BookingState.ACTIVE)),
            f.repository.all());
        assertTrue(f.notifier.requests().isEmpty());
    }
}
