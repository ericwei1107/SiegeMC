package woo.siegePlugin.team;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TownyActionBypassListenerTest {

    @Test
    void grantsOnlyTownyGameplayProtectionBypasses() {
        assertEquals(List.of("towny.wild.*", "towny.claimed.*"), TownyActionBypassListener.ACTION_BYPASS_PERMISSIONS);
        assertFalse(TownyActionBypassListener.ACTION_BYPASS_PERMISSIONS.contains("towny.admin"));
    }
}
