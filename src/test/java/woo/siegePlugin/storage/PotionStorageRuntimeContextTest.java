package woo.siegePlugin.storage;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.map.MapBounds;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionStorageRuntimeContextTest {

    private static final MapBounds BOUNDS = new MapBounds(-100, -100, 100, 100);

    @Test
    void calibrationMutationsDoNotChangePublishedRoundSnapshot() {
        PotionStorageKey ironMountain = key("iron_mountain1", 10);
        PotionStorageService.RuntimeContext round = PotionStorageService.RuntimeContext.forKeys(
                "iron_mountain1", "siege-active-round", BOUNDS, List.of(ironMountain)
        );
        PotionStorageService.RuntimeContext calibration = PotionStorageService.RuntimeContext.forKeys(
                "iron_mountain1", "siege-active-calibration", BOUNDS, List.of(ironMountain)
        );

        PotionStorageKey replacement = key("iron_mountain1", 20);
        calibration.remove(ironMountain);
        calibration.addKey(replacement);

        assertTrue(round.contains(ironMountain));
        assertFalse(round.contains(replacement));
        assertFalse(calibration.contains(ironMountain));
        assertTrue(calibration.contains(replacement));
    }

    @Test
    void roundAndCalibrationLocksAreIndependent() {
        PotionStorageKey ironMountain = key("iron_mountain1", 10);
        PotionStorageService.RuntimeContext round = PotionStorageService.RuntimeContext.forKeys(
                "iron_mountain1", "siege-active-round", BOUNDS, List.of(ironMountain)
        );
        PotionStorageService.RuntimeContext calibration = PotionStorageService.RuntimeContext.forKeys(
                "iron_mountain1", "siege-active-calibration", BOUNDS, List.of(ironMountain)
        );
        UUID fighter = UUID.randomUUID();
        UUID admin = UUID.randomUUID();

        UUID storage = UUID.randomUUID();
        assertTrue(round.locks().acquire(storage, fighter));
        assertTrue(calibration.locks().acquire(storage, admin));

        calibration.locks().clear();

        assertTrue(round.locks().isHolder(storage, fighter));
    }

    @Test
    void calibrationAdditionRetainsExistingIronMountainKeys() {
        PotionStorageKey firstIronMountainSupply = key("iron_mountain1", 10);
        PotionStorageKey secondIronMountainSupply = key("iron_mountain1", 20);
        PotionStorageService.RuntimeContext calibration = PotionStorageService.RuntimeContext.forKeys(
                "iron_mountain1", "siege-active-calibration", BOUNDS,
                List.of(firstIronMountainSupply, secondIronMountainSupply)
        );

        PotionStorageKey calibrationAddition = key("iron_mountain1", 30);
        calibration.addKey(calibrationAddition);

        assertTrue(calibration.contains(firstIronMountainSupply));
        assertTrue(calibration.contains(secondIronMountainSupply));
        assertTrue(calibration.contains(calibrationAddition));
    }

    private static PotionStorageKey key(String mapId, int x) {
        return new PotionStorageKey(
                new MapChestLocation(mapId, x, 64, 10),
                new MapChestLocation(mapId, x + 1, 64, 10)
        );
    }
}
