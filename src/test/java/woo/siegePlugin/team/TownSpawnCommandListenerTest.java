package woo.siegePlugin.team;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownSpawnCommandListenerTest {

    @Test
    void recognizesOnlyTownSpawnAliases() {
        assertTrue(TownSpawnCommandListener.isTownSpawnCommand("/t spawn"));
        assertTrue(TownSpawnCommandListener.isTownSpawnCommand("/town spawn"));
        assertTrue(TownSpawnCommandListener.isTownSpawnCommand("/towny:t spawn"));
        assertFalse(TownSpawnCommandListener.isTownSpawnCommand("/t"));
        assertFalse(TownSpawnCommandListener.isTownSpawnCommand("/t spawn another-town"));
        assertFalse(TownSpawnCommandListener.isTownSpawnCommand("/siege spawn"));
    }
}
