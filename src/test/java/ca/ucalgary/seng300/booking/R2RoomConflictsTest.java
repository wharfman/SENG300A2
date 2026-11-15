package ca.ucalgary.seng300.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static ca.ucalgary.seng300.booking.TestFixtures.at;
import static org.junit.jupiter.api.Assertions.*;

/*
 * Tests for R2 (room conflicts).
 *
 * R2: ACTIVE bookings in the same room can't overlap, and an overlap gives CONFLICT.
 * A booking can start exactly when another ends. Different rooms and bookings that
 * aren't ACTIVE don't conflict. Otherwise we get CREATED and one new ACTIVE booking.
 *
 * Most tests use one existing booking b1: ACTIVE, room R204, at(120) to at(240),
 * owned by student-b. Requests come from student-a and always have valid times
 * so R1 doesn't get in the way.
 */
@DisplayName("R2 - Room conflicts")
class R2RoomConflictsTest {

    private static final String ROOM = "R204";
    private static final String REQUESTER = "student-a";
    private static final String B1_OWNER = "student-b";

    // Seeds the b1 booking that most of the tests check against.
    private static Booking seedB1(TestFixtures f, BookingState state) {
        return f.seed("b1", ROOM, B1_OWNER, 120, 240, state);
    }

    // Everything that should be true when a request is a conflict. We don't just check
    // the status, because a bug could return CONFLICT but still save the booking or
    // change one of the existing ones.
    private static void assertConflict(TestFixtures f, CreateResult result, List<Booking> bookingsBefore) {
        assertEquals(CreateStatus.CONFLICT, result.status());
        assertNull(result.bookingId(), "conflicting requests shouldn't get a booking ID");
        assertEquals(bookingsBefore, f.repository.all(), "nothing should be saved or changed");
        assertTrue(f.notifier.requests().isEmpty(), "creating a booking should never notify (R5)");
    }

    // Everything that should be true when a request goes through: the expected ID comes
    // back, the new booking is saved as ACTIVE with what we asked for, and the bookings
    // that were already there are still exactly the same.
    // (Compared as sets so the test doesn't depend on what order the repository lists things in.)
    private static void assertCreated(TestFixtures f, CreateResult result, String expectedId,
                                      String room, String owner, Instant start, Instant end,
                                      List<Booking> bookingsBefore) {
        assertEquals(CreateStatus.CREATED, result.status());
        assertEquals(expectedId, result.bookingId());

        Booking expected = new Booking(expectedId, room, owner, start, end, BookingState.ACTIVE);
        assertEquals(expected, f.repository.find(expectedId), "new booking should be ACTIVE with the requested details");

        List<Booking> expectedAll = new ArrayList<>(bookingsBefore);
        expectedAll.add(expected);
        assertEquals(expectedAll.size(), f.repository.all().size(), "should add exactly one booking");
        assertEquals(new HashSet<>(expectedAll), new HashSet<>(f.repository.all()),
            "existing bookings should be unchanged");
        assertTrue(f.notifier.requests().isEmpty(), "creating a booking should never notify (R5)");
    }

    // Requests that share at least one minute with b1. The 1 min overlap cases are the
    // boundary ones, right next to the touching cases in the next test.
    @ParameterizedTest(name = "{0}: at({1}) to at({2})")
    @CsvSource({
        "exact same times,               120, 240",
        "overlaps the start of b1,        60, 180",
        "overlaps the end of b1,         180, 300",
        "inside b1,                      150, 210",
        "covers all of b1,                60, 300",
        "same start and ends earlier,    120, 180",
        "same end and starts later,      180, 240",
        "1 min overlap at start of b1,    60, 121",
        "1 min overlap at end of b1,     239, 300"
    })
    void overlappingRequestsInTheSameRoomAreConflicts(String description, long start, long end) {
        TestFixtures f = new TestFixtures();
        Booking b1 = seedB1(f, BookingState.ACTIVE);

        CreateResult result = f.service.create(ROOM, REQUESTER, at(start), at(end));

        assertConflict(f, result, List.of(b1));
    }

    // Requests that don't share any minutes with b1, or are in a different room.
    // Touching is allowed both ways (ending when b1 starts, or starting when b1 ends).
    @ParameterizedTest(name = "{0}: {1} at({2}) to at({3})")
    @CsvSource({
        "touches the start of b1, R204,  60, 120",
        "1 min gap before b1,     R204,  60, 119",
        "starts when b1 ends,     R204, 240, 300",
        "1 min gap after b1,      R204, 241, 300",
        "different room,          R105, 120, 240"
    })
    void touchingSeparateOrOtherRoomRequestsAreCreated(String description, String room, long start, long end) {
        TestFixtures f = new TestFixtures();
        Booking b1 = seedB1(f, BookingState.ACTIVE);

        CreateResult result = f.service.create(room, REQUESTER, at(start), at(end));

        assertCreated(f, result, "created-1", room, REQUESTER, at(start), at(end), List.of(b1));
    }

    // R2 doesn't say anything about owners, so overlapping your own booking is still a conflict.
    @Test
    @DisplayName("overlapping your own booking is still a conflict")
    void sameOwnerOverlapIsStillAConflict() {
        TestFixtures f = new TestFixtures();
        Booking b1 = seedB1(f, BookingState.ACTIVE);

        CreateResult result = f.service.create(ROOM, B1_OWNER, at(150), at(210));

        assertConflict(f, result, List.of(b1));
    }

    // Only ACTIVE bookings block a room. Same exact times as b1, but b1 isn't active.
    // (The ACTIVE version of this is the "exact same times" conflict case above.)
    @ParameterizedTest(name = "b1 {0} doesn't block the room")
    @EnumSource(value = BookingState.class, names = {"CANCELLED", "COMPLETED"})
    void inactiveBookingsDoNotConflict(BookingState state) {
        TestFixtures f = new TestFixtures();
        Booking b1 = seedB1(f, state);

        CreateResult result = f.service.create(ROOM, REQUESTER, at(120), at(240));

        assertCreated(f, result, "created-1", ROOM, REQUESTER, at(120), at(240), List.of(b1));
    }

    // The next three have more than one booking already in the room, since the conflict
    // might not be with the first booking that gets checked.

    @Test
    @DisplayName("conflict with the second booking, not the first")
    void conflictWithSecondBookingIsFound() {
        TestFixtures f = new TestFixtures();
        Booking b1 = seedB1(f, BookingState.ACTIVE);
        Booking b2 = f.seed("b2", ROOM, "student-c", 360, 480, BookingState.ACTIVE);

        CreateResult result = f.service.create(ROOM, REQUESTER, at(400), at(450));

        assertConflict(f, result, List.of(b1, b2));
    }

    @Test
    @DisplayName("fits exactly in the gap between two bookings")
    void requestThatFitsExactlyInTheGapIsCreated() {
        TestFixtures f = new TestFixtures();
        Booking b1 = seedB1(f, BookingState.ACTIVE);
        Booking b2 = f.seed("b2", ROOM, "student-c", 360, 480, BookingState.ACTIVE);

        CreateResult result = f.service.create(ROOM, REQUESTER, at(240), at(360));

        assertCreated(f, result, "created-1", ROOM, REQUESTER, at(240), at(360), List.of(b1, b2));
    }

    @Test
    @DisplayName("a cancelled booking doesn't hide an active one at the same time")
    void cancelledBookingDoesNotHideActiveOne() {
        TestFixtures f = new TestFixtures();
        Booking b1 = seedB1(f, BookingState.CANCELLED);
        Booking b2 = f.seed("b2", ROOM, "student-c", 120, 240, BookingState.ACTIVE);

        CreateResult result = f.service.create(ROOM, REQUESTER, at(150), at(210));

        assertConflict(f, result, List.of(b1, b2));
    }

    // Sequence from our plan: a booking made through the service (not seeded) should block
    // later requests too. The conflict in the middle shouldn't use up an ID, so the last
    // booking gets created-2 (we added this after reading the code).
    @Test
    @DisplayName("create, then conflicting create, then touching create")
    void bookingMadeThroughServiceBlocksLaterRequests() {
        TestFixtures f = new TestFixtures();

        CreateResult first = f.service.create(ROOM, REQUESTER, at(120), at(240));
        assertCreated(f, first, "created-1", ROOM, REQUESTER, at(120), at(240), List.of());
        List<Booking> afterFirst = f.repository.all();

        CreateResult second = f.service.create(ROOM, B1_OWNER, at(180), at(300));
        assertConflict(f, second, afterFirst);

        CreateResult third = f.service.create(ROOM, B1_OWNER, at(240), at(300));
        assertCreated(f, third, "created-2", ROOM, B1_OWNER, at(240), at(300), afterFirst);
    }
}
