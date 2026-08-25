package woo.siegePlugin.cycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegePhaseStatusTest {

    @Test
    void theStandInPhaseMatchesStage4_4h1BootState() {
        assertTrue(SiegePhaseStatus.alwaysActive().isActive());
    }

    @Test
    void isActiveTracksTheReportedPhase() {
        SiegePhaseStatus breaking = () -> SiegePhase.BREAK;

        assertFalse(breaking.isActive());
        assertTrue(((SiegePhaseStatus) () -> SiegePhase.ACTIVE).isActive());
    }
}
