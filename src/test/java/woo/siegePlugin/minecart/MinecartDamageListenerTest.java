package woo.siegePlugin.minecart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecartDamageListenerTest {

    @Test
    void countsEveryLivingNonSpectatorInsideTheCaptureZone() {
        assertTrue(MinecartDamageListener.countsAsNearbyFighter(true, false, false, true));
    }

    @Test
    void excludesDeadOfflineSpectatorAndOutsidePlayers() {
        assertFalse(MinecartDamageListener.countsAsNearbyFighter(false, false, false, true));
        assertFalse(MinecartDamageListener.countsAsNearbyFighter(true, true, false, true));
        assertFalse(MinecartDamageListener.countsAsNearbyFighter(true, false, true, true));
        assertFalse(MinecartDamageListener.countsAsNearbyFighter(true, false, false, false));
    }
}
