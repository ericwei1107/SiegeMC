package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static woo.siegePlugin.arena.BaseClaimInteractionListener.AccessDecision.ALLOW_CLAIM;
import static woo.siegePlugin.arena.BaseClaimInteractionListener.AccessDecision.BLOCK_COMBAT;
import static woo.siegePlugin.arena.BaseClaimInteractionListener.AccessDecision.DENY_CLAIM;
import static woo.siegePlugin.arena.BaseClaimInteractionListener.AccessDecision.NONE;

class BaseClaimInteractionListenerTest {

    @Test
    void owningTeamAlwaysKeepsClaimedEntranceAccess() {
        assertEquals(ALLOW_CLAIM,
                BaseClaimInteractionListener.decide(Team.RED, Team.RED, true, true, false));
        assertEquals(ALLOW_CLAIM,
                BaseClaimInteractionListener.decide(Team.RED, Team.RED, true, true, true));
    }

    @Test
    void foreignAndUnrosteredPlayersCannotUseClaimedEntrances() {
        assertEquals(DENY_CLAIM,
                BaseClaimInteractionListener.decide(Team.RED, Team.BLUE, true, true, false));
        assertEquals(DENY_CLAIM,
                BaseClaimInteractionListener.decide(Team.RED, null, true, true, false));
    }

    @Test
    void combatGateRestrictionStillAppliesOutsideClaimsOnly() {
        assertEquals(BLOCK_COMBAT,
                BaseClaimInteractionListener.decide(null, Team.RED, true, true, true));
        assertEquals(NONE,
                BaseClaimInteractionListener.decide(null, Team.RED, true, true, false));
        assertEquals(NONE,
                BaseClaimInteractionListener.decide(null, Team.RED, true, false, true));
    }
}
