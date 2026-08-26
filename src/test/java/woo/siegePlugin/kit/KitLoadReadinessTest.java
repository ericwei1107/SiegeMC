package woo.siegePlugin.kit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitLoadReadinessTest {

    @Test
    void editorOnlyBecomesReadyWhenItsCurrentLoadCompletes() {
        KitLoadReadiness readiness = new KitLoadReadiness();
        UUID playerId = UUID.randomUUID();

        long firstLoad = readiness.begin(playerId);
        assertFalse(readiness.isReady(playerId));
        assertTrue(readiness.complete(playerId, firstLoad));
        assertTrue(readiness.isReady(playerId));
    }

    @Test
    void staleLoadCompletionCannotReplaceANewerJoinLoad() {
        KitLoadReadiness readiness = new KitLoadReadiness();
        UUID playerId = UUID.randomUUID();

        long firstLoad = readiness.begin(playerId);
        long secondLoad = readiness.begin(playerId);

        assertFalse(readiness.complete(playerId, firstLoad));
        assertFalse(readiness.isReady(playerId));
        assertTrue(readiness.complete(playerId, secondLoad));
        assertTrue(readiness.isReady(playerId));
    }

    @Test
    void disconnectInvalidatesAnInFlightLoad() {
        KitLoadReadiness readiness = new KitLoadReadiness();
        UUID playerId = UUID.randomUUID();

        long load = readiness.begin(playerId);
        readiness.forget(playerId);

        assertFalse(readiness.complete(playerId, load));
        assertFalse(readiness.isReady(playerId));
    }
}
