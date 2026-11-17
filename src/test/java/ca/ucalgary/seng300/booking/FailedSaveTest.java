package ca.ucalgary.seng300.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static ca.ucalgary.seng300.booking.TestFixtures.at;
import static org.junit.jupiter.api.Assertions.*;

/*
 * Part 6 Option B: what happens when saving a cancellation fails.
 *
 * TestFixtures always uses the normal in-memory repository, which never fails, so here
 * we build the BookingService ourselves with FailingSaveRepository. Everything else is
 * the same as TestFixtures: the same fixed clock and the recording notifier.
 *
 * The cancellation is otherwise valid (owner, ACTIVE, 240 minutes before it starts),
 * so the only reason it can't finish is the failed save.
 */
@DisplayName("Part 6 - Failed save during cancellation")
class FailedSaveTest {

    private static final String OWNER = "student-a";

    private final FailingSaveRepository repository = new FailingSaveRepository();
    private final TestFixtures.RecordingNotifier notifier = new TestFixtures.RecordingNotifier();
    private final BookingService service = new BookingService(repository, notifier,
        Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC), () -> "created-1");

    private Booking b1;
    private Booking b2;

    // Seed through save() while failures are still off
    private void seedBookings() {
        b1 = new Booking("b1", "R204", OWNER, at(240), at(300), BookingState.ACTIVE);
        b2 = new Booking("b2", "R105", "student-c", at(300), at(360), BookingState.ACTIVE);
        repository.save(b1);
        repository.save(b2);
    }

    @Test
    @DisplayName("failed save: exception reaches the caller, nothing changes, no notification")
    void failedSaveDuringValidCancellation() {
        seedBookings();
        List<Booking> before = repository.all();
        repository.failSaves();

        RepositoryException thrown = assertThrows(RepositoryException.class,
            () -> service.cancel("b1", OWNER),
            "the save failure should reach the caller instead of being hidden");

        assertTrue(thrown.getMessage().contains("b1"), "the failure should be from saving b1");
        assertEquals(1, repository.failedSaveAttempts(), "cancel should have tried to save exactly once");
        assertEquals(before, repository.all(), "b1 should still be ACTIVE and b2 unchanged");
        assertEquals(BookingState.ACTIVE, repository.find("b1").state());
        assertTrue(notifier.requests().isEmpty(), "no notification if the cancellation wasn't saved");
    }

    // Control: the exact same setup works when the save doesn't fail. This shows the
    // test above fails only because of the save, not because the cancellation was invalid.
    @Test
    @DisplayName("control: same cancellation succeeds when the save works")
    void sameCancellationSucceedsWhenSaveWorks() {
        seedBookings();

        CancelStatus result = service.cancel("b1", OWNER);

        Booking b1Cancelled = b1.withState(BookingState.CANCELLED);
        assertEquals(CancelStatus.CANCELLED, result);
        assertEquals(List.of(b1Cancelled, b2), repository.all());
        assertEquals(List.of(b1Cancelled), notifier.requests());
        assertEquals(0, repository.failedSaveAttempts());
    }
}
