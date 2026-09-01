package woo.siegePlugin.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTaggedCommandListenerTest {

    @Test
    void taggedPlayersAreBlockedFromEveryCommand() {
        assertTrue(CombatTaggedCommandListener.blocksCommand(true));
    }

    @Test
    void untaggedPlayersAreNotBlocked() {
        assertFalse(CombatTaggedCommandListener.blocksCommand(false));
    }
}
