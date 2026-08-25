package woo.siegePlugin.team;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamSwitchServiceTest {

    @Test
    void allowsMoveWhenDestinationWouldLeadByOnlyOne() {
        assertFalse(TeamSwitchService.wouldCreateTwoPlayerLead(5, 4));
    }

    @Test
    void blocksMoveWhenDestinationWouldLeadByTwoOrMoreAfterMove() {
        assertTrue(TeamSwitchService.wouldCreateTwoPlayerLead(5, 5));
        assertTrue(TeamSwitchService.wouldCreateTwoPlayerLead(4, 5));
    }

    @Test
    void enforcesFifteenMinuteCooldown() {
        Instant lastSwitch = Instant.parse("2026-08-25T12:00:00Z");

        assertEquals(
                Duration.ofSeconds(30),
                TeamSwitchService.calculateCooldownRemaining(
                        lastSwitch,
                        lastSwitch.plus(Duration.ofMinutes(14).plusSeconds(30))
                )
        );
        assertEquals(
                Duration.ZERO,
                TeamSwitchService.calculateCooldownRemaining(lastSwitch, lastSwitch.plus(Duration.ofMinutes(15)))
        );
    }
}
