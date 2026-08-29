package woo.siegePlugin.score;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.round.RoundActivityStatus;
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
        RoundActivityStatus active = () -> true;

        assertTrue(ScoringService.shouldApplySessionPoints(ScoreReason.BANNER_CONTROL, active, 4L, 4L));
        assertFalse(ScoringService.shouldApplySessionPoints(ScoreReason.BANNER_CONTROL, active, 4L, 5L));
    }

    @Test
    void sessionPointsRejectCallbacksWhileTheCycleIsOnBreakOrForDeathRewards() {
        RoundActivityStatus breaking = () -> false;
        RoundActivityStatus active = () -> true;

        assertFalse(ScoringService.shouldApplySessionPoints(ScoreReason.BANNER_CONTROL, breaking, 4L, 4L));
        assertFalse(ScoringService.shouldApplySessionPoints(ScoreReason.ENEMY_DEATH_BONUS, active, 4L, 4L));
    }

    @Test
    void bannerCurrencyTargetsOnlyTheCurrentCompletedControllersDuringActive() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BannerControlStatus control = control(Team.RED, Set.of(first, second));

        assertEquals(Set.of(first, second), ScoringService.rewardableControllerIds(() -> true, control));
        assertTrue(ScoringService.rewardableControllerIds(() -> false, control).isEmpty());
    }

    @Test
    void bannerCurrencyDoesNotPayWithoutControl() {
        assertTrue(ScoringService.rewardableControllerIds(
                () -> true,
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
