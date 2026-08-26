package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaMaintenanceCoordinatorTest {

    @Test
    void captureExcludesResetUntilReleased() {
        ArenaMaintenanceCoordinator maintenance = new ArenaMaintenanceCoordinator();

        assertTrue(maintenance.beginCapture());
        assertFalse(maintenance.beginResetCountdown());
        assertEquals(ArenaMaintenanceCoordinator.State.CAPTURING, maintenance.state());

        maintenance.finishCapture();
        assertTrue(maintenance.beginResetCountdown());
    }

    @Test
    void resetExcludesCaptureThroughCountdownAndRestore() {
        ArenaMaintenanceCoordinator maintenance = new ArenaMaintenanceCoordinator();

        assertTrue(maintenance.beginResetCountdown());
        assertFalse(maintenance.beginCapture());
        assertTrue(maintenance.beginRestore());
        assertFalse(maintenance.beginCapture());

        maintenance.finishReset();
        assertEquals(ArenaMaintenanceCoordinator.State.IDLE, maintenance.state());
    }
}
