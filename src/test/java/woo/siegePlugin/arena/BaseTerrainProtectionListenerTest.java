package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseTerrainProtectionListenerTest {

    @Test
    void owningFightersCanPlaceInsideTheirClaim() {
        assertTrue(BaseTerrainProtectionListener.allowsPlace(Team.RED, Team.RED));
        assertFalse(BaseTerrainProtectionListener.allowsPlace(Team.RED, Team.BLUE));
    }

    @Test
    void onlyOwningFightersCanBreakTrackedPlacedBlocksInTheirClaim() {
        assertTrue(BaseTerrainProtectionListener.allowsBreak(Team.RED, Team.RED, true));
        assertFalse(BaseTerrainProtectionListener.allowsBreak(Team.RED, Team.RED, false));
        assertFalse(BaseTerrainProtectionListener.allowsBreak(Team.RED, Team.BLUE, true));
    }
}
