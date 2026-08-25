package woo.siegePlugin.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureEligibilityTest {

    private static boolean eligible(
            boolean online,
            boolean dead,
            boolean spectating,
            boolean flying,
            boolean gliding,
            boolean onTeam,
            boolean withinZone
    ) {
        return CaptureEligibility.isEligible(online, dead, spectating, flying, gliding, onTeam, withinZone);
    }

    @Test
    void acceptsALivingOnlineSurvivalTeamPlayerInsideTheZone() {
        assertTrue(eligible(true, false, false, false, false, true, true));
    }

    @Test
    void rejectsEachDisqualifyingConditionOnItsOwn() {
        assertFalse(eligible(false, false, false, false, false, true, true), "offline");
        assertFalse(eligible(true, true, false, false, false, true, true), "dead");
        assertFalse(eligible(true, false, true, false, false, true, true), "spectating");
        assertFalse(eligible(true, false, false, true, false, true, true), "flying");
        assertFalse(eligible(true, false, false, false, true, true, true), "gliding");
        assertFalse(eligible(true, false, false, false, false, false, true), "no team");
        assertFalse(eligible(true, false, false, false, false, true, false), "outside zone");
    }
}
