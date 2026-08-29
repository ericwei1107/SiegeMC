package woo.siegePlugin.stats;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MVP accumulation is bound to one explicit match.
 *
 * <p>Without that binding, a damage or banner tick arriving just after a round
 * closed would be attributed to the next round's ceremony.</p>
 */
class MatchStatsTrackerTest {

    private final MatchStatsTracker tracker = new MatchStatsTracker();
    private final UUID player = UUID.randomUUID();

    @Test
    void nothingAccumulatesBeforeAMatchIsBound() {
        tracker.recordKill("rotation-1", player, "A");
        tracker.recordDamage("rotation-1", player, "A", 10D);
        tracker.recordBannerSecond("rotation-1", player, "A");

        assertTrue(tracker.snapshot().isEmpty());
        assertFalse(tracker.isBoundTo("rotation-1"));
    }

    @Test
    void recordsForADifferentMatchAreDropped() {
        tracker.bind("rotation-2");
        tracker.recordKill("rotation-1", player, "A");
        tracker.recordDamage("rotation-1", player, "A", 25D);

        assertTrue(tracker.snapshot().isEmpty(), "a late record from the old round must not leak in");
    }

    @Test
    void rebindingDiscardsThePreviousMatchEntirely() {
        tracker.bind("rotation-1");
        tracker.recordKill("rotation-1", player, "A");
        assertEquals(1, tracker.snapshot().size());

        tracker.bind("rotation-2");
        assertTrue(tracker.snapshot().isEmpty());
        assertTrue(tracker.isBoundTo("rotation-2"));
    }

    @Test
    void aRejectedKillIsRolledBackOnlyForItsOwnMatch() {
        tracker.bind("rotation-1");
        tracker.recordKill("rotation-1", player, "A");
        tracker.recordKill("rotation-1", player, "A");

        tracker.rollbackKill("rotation-9", player);
        assertEquals(2L, tracker.snapshot().iterator().next().kills(), "a foreign rollback is ignored");

        tracker.rollbackKill("rotation-1", player);
        assertEquals(1L, tracker.snapshot().iterator().next().kills());
    }

    @Test
    void restoringACheckpointBindsAndReplacesTheAccumulator() {
        tracker.bind("rotation-1");
        tracker.recordKill("rotation-1", player, "A");

        UUID other = UUID.randomUUID();
        tracker.restore("rotation-2", List.of(new PlayerMatchStats(other, "B", 5L, 3.5D, 60L)));

        assertTrue(tracker.isBoundTo("rotation-2"));
        assertEquals(1, tracker.snapshot().size());
        PlayerMatchStats restored = tracker.snapshot().iterator().next();
        assertEquals(other, restored.playerId());
        assertEquals(5L, restored.kills());
        assertEquals(60L, restored.bannerSeconds());
    }

    @Test
    void unbindingStopsEveryFurtherRecord() {
        tracker.bind("rotation-1");
        tracker.recordKill("rotation-1", player, "A");
        tracker.unbind();

        tracker.recordKill("rotation-1", player, "A");
        assertTrue(tracker.snapshot().isEmpty());
    }
}
