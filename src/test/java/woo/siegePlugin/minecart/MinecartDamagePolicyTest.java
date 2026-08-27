package woo.siegePlugin.minecart;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecartDamagePolicyTest {

    private final MinecartDamagePolicy policy = new MinecartDamagePolicy(
            new MinecartDamageSettings(0.825D, 7)
    );

    @Test
    void exactSevenPlayerLeadReceivesFullVanillaDamage() {
        MinecartHeadcounts headcounts = new MinecartHeadcounts(10, 3);

        assertTrue(policy.usesFullVanillaDamage(Team.RED, headcounts));
        assertEquals(20.0D, policy.scaledRawDamage(20.0D, Team.RED, headcounts));
    }

    @Test
    void outnumberedTeamAlwaysReceivesBalancedDamage() {
        MinecartHeadcounts headcounts = new MinecartHeadcounts(10, 3);

        assertFalse(policy.usesFullVanillaDamage(Team.BLUE, headcounts));
        assertEquals(16.5D, policy.scaledRawDamage(20.0D, Team.BLUE, headcounts));
    }

    @Test
    void leadBelowSevenStillReceivesBalancedDamage() {
        MinecartHeadcounts headcounts = new MinecartHeadcounts(9, 3);

        assertFalse(policy.usesFullVanillaDamage(Team.RED, headcounts));
        assertEquals(16.5D, policy.scaledRawDamage(20.0D, Team.RED, headcounts));
    }

    @Test
    void tiedTeamsBothReceiveBalancedDamage() {
        MinecartHeadcounts headcounts = new MinecartHeadcounts(4, 4);

        assertEquals(16.5D, policy.scaledRawDamage(20.0D, Team.RED, headcounts));
        assertEquals(16.5D, policy.scaledRawDamage(20.0D, Team.BLUE, headcounts));
    }

    @Test
    void thresholdAndCoefficientRemainConfigurable() {
        MinecartDamagePolicy custom = new MinecartDamagePolicy(new MinecartDamageSettings(0.5D, 3));
        MinecartHeadcounts headcounts = new MinecartHeadcounts(5, 2);

        assertEquals(20.0D, custom.scaledRawDamage(20.0D, Team.RED, headcounts));
        assertEquals(10.0D, custom.scaledRawDamage(20.0D, Team.BLUE, headcounts));
    }
}
