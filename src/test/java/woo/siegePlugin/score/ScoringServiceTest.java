package woo.siegePlugin.score;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.cycle.SiegePhase;
import woo.siegePlugin.cycle.SiegePhaseStatus;
import woo.siegePlugin.persistence.ScoreReason;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoringServiceTest {

    @Test
    void sessionPointsOnlyAcceptCallbacksFromTheCurrentActiveWindow() {
        SiegePhaseStatus active = () -> SiegePhase.ACTIVE;

        assertTrue(ScoringService.shouldApplySessionPoints(ScoreReason.BANNER_CONTROL, active, 4L, 4L));
        assertFalse(ScoringService.shouldApplySessionPoints(ScoreReason.BANNER_CONTROL, active, 4L, 5L));
    }

    @Test
    void sessionPointsRejectCallbacksWhileTheCycleIsOnBreakOrForDeathRewards() {
        SiegePhaseStatus breaking = () -> SiegePhase.BREAK;
        SiegePhaseStatus active = () -> SiegePhase.ACTIVE;

        assertFalse(ScoringService.shouldApplySessionPoints(ScoreReason.BANNER_CONTROL, breaking, 4L, 4L));
        assertFalse(ScoringService.shouldApplySessionPoints(ScoreReason.ENEMY_DEATH_BONUS, active, 4L, 4L));
    }
}
