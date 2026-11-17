package ca.ucalgary.seng300.booking;

import java.util.List;

/*
 * Test double for Part 6 Option B.
 *
 * Works like the normal in-memory repository, except that once failSaves() is called,
 * every save throws a RepositoryException BEFORE anything is stored. This matches
 * API.md: "a RepositoryException from save occurs before any data change".
 *
 * Saves only start failing after we call failSaves(), so seeding test data with
 * save() still works and the failure never happens during fixture setup.
 */
class FailingSaveRepository implements BookingRepository {

    private final InMemoryBookingRepository stored = new InMemoryBookingRepository();
    private boolean failing = false;
    private int failedSaveAttempts = 0;

    // Turn on failures. Call this after seeding.
    void failSaves() {
        failing = true;
    }

    // How many times something tried to save while failures were on.
    int failedSaveAttempts() {
        return failedSaveAttempts;
    }

    @Override
    public Booking find(String id) {
        return stored.find(id);
    }

    @Override
    public List<Booking> all() {
        return stored.all();
    }

    @Override
    public void save(Booking booking) {
        if (failing) {
            failedSaveAttempts++;
            // Throw first so the stored data never changes
            throw new RepositoryException("simulated save failure for " + booking.id());
        }
        stored.save(booking);
    }
}
