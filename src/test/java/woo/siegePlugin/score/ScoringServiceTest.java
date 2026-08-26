package woo.siegePlugin.score;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.cycle.SiegePhase;
import woo.siegePlugin.cycle.SiegePhaseStatus;
import woo.siegePlugin.capture.BannerControlStatus;
import woo.siegePlugin.persistence.ScoreReason;
import woo.siegePlugin.team.Team;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void bannerCurrencyTargetsOnlyTheCurrentCompletedControllersDuringActive() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BannerControlStatus control = control(Team.RED, Set.of(first, second));

        assertEquals(Set.of(first, second), ScoringService.rewardableControllerIds(() -> SiegePhase.ACTIVE, control));
        assertTrue(ScoringService.rewardableControllerIds(() -> SiegePhase.BREAK, control).isEmpty());
    }

    @Test
    void bannerCurrencyDoesNotPayWithoutControl() {
        assertTrue(ScoringService.rewardableControllerIds(
                () -> SiegePhase.ACTIVE,
                control(null, Set.of(UUID.randomUUID()))
        ).isEmpty());
    }

    private static BannerControlStatus control(Team team, Set<UUID> controllerIds) {
        return new BannerControlStatus() {
            @Override
            public Optional<Team> controllingTeam() {
                return Optional.ofNullable(team);
            }

            @Override
            public int controllerCount() {
                return controllerIds.size();
            }

            @Override
            public Set<UUID> controllerIds() {
                return controllerIds;
            }
        };
    }
}
