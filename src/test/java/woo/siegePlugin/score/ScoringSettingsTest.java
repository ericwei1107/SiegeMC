package woo.siegePlugin.score;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoringSettingsTest {

    private final ScoringSettings settings = new ScoringSettings(10L, 150L);

    @Test
    void scalesPointsByControllerCount() {
        assertEquals(0L, settings.pointsForControllers(0));
        assertEquals(10L, settings.pointsForControllers(1));
        assertEquals(30L, settings.pointsForControllers(3));
    }

    @Test
    void treatsANegativeControllerCountAsNoControllers() {
        assertEquals(0L, settings.pointsForControllers(-1));
    }

    @Test
    void exposesAFlatDeathBonusIndependentOfBannerControl() {
        assertEquals(150L, settings.enemyDeathBonusPoints());
    }

    @Test
    void allowsEitherRewardToBeDisabledIndependently() {
        assertEquals(0L, new ScoringSettings(0L, 150L).pointsForControllers(5));
        assertEquals(0L, new ScoringSettings(10L, 0L).enemyDeathBonusPoints());
    }

    @Test
    void rejectsNegativePointValues() {
        assertThrows(IllegalArgumentException.class, () -> new ScoringSettings(-1L, 150L));
        assertThrows(IllegalArgumentException.class, () -> new ScoringSettings(10L, -1L));
    }
}
