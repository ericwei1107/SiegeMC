package woo.siegePlugin.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureBeaconTest {

    @Test
    void needsTwoBlocksBelowTheBannerForTheVanillaBeaconAndItsBase() {
        assertTrue(CaptureBeacon.isSupported(-60, -64));
        assertFalse(CaptureBeacon.isSupported(-63, -64));
    }
}
