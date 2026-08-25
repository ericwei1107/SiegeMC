package woo.siegePlugin.score;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoringSettingsTest {

    private final ScoringSettings settings = new ScoringSettings(10L);

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
    void allowsScoringToBeDisabledWithZeroBasePoints() {
        assertEquals(0L, new ScoringSettings(0L).pointsForControllers(5));
    }

    @Test
    void rejectsNegativeBasePoints() {
        assertThrows(IllegalArgumentException.class, () -> new ScoringSettings(-1L));
    }
}
