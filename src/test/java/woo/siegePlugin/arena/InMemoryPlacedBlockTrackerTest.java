package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPlacedBlockTrackerTest {

    @Test
    void tracksExactWorldAndBlockCoordinates() {
        InMemoryPlacedBlockTracker tracker = new InMemoryPlacedBlockTracker();
        tracker.record("siegeworld", 10, 64, -4);

        assertTrue(tracker.contains("siegeworld", 10, 64, -4));
        assertFalse(tracker.contains("siegeworld", 11, 64, -4));
        assertFalse(tracker.contains("other-world", 10, 64, -4));
    }

    @Test
    void removesOnlyTheBrokenEntryAndClearsAtReset() {
        InMemoryPlacedBlockTracker tracker = new InMemoryPlacedBlockTracker();
        tracker.record("siegeworld", 10, 64, -4);
        tracker.record("siegeworld", 11, 64, -4);

        assertTrue(tracker.remove("siegeworld", 10, 64, -4));
        assertEquals(1, tracker.size());
        tracker.clearAll();
        assertEquals(0, tracker.size());
    }
}
