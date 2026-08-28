package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaSnapshotLimitsTest {

    @Test
    void evaluatesTheWholeInclusiveRegion() {
        ArenaSnapshotLimits limits = new ArenaSnapshotLimits(64);
        assertTrue(limits.permits(ArenaRegion.between("world", 0, 0, 0, 3, 3, 3)));
        assertFalse(limits.permits(ArenaRegion.between("world", 0, 0, 0, 4, 3, 3)));
    }
}
