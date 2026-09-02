package woo.siegePlugin.combat;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTaggedInteractionListenerTest {

    @Test
    void taggedPlayersCannotRightClickAnyFenceGate() {
        assertTrue(CombatTaggedInteractionListener.blocksFenceGate(
                Action.RIGHT_CLICK_BLOCK, Material.OAK_FENCE_GATE, true, false
        ));
        assertTrue(CombatTaggedInteractionListener.blocksFenceGate(
                Action.RIGHT_CLICK_BLOCK, Material.WARPED_FENCE_GATE, true, false
        ));
    }

    @Test
    void untaggedPlayersAndOtherInteractionsRemainAllowed() {
        assertFalse(CombatTaggedInteractionListener.blocksFenceGate(
                Action.RIGHT_CLICK_BLOCK, Material.OAK_FENCE_GATE, false, false
        ));
        assertFalse(CombatTaggedInteractionListener.blocksFenceGate(
                Action.RIGHT_CLICK_BLOCK, Material.OAK_DOOR, true, false
        ));
        assertFalse(CombatTaggedInteractionListener.blocksFenceGate(
                Action.LEFT_CLICK_BLOCK, Material.OAK_FENCE_GATE, true, false
        ));
    }

    @Test
    void taggedPlayersCanUseGatesInsideTheirOwnClaim() {
        assertFalse(CombatTaggedInteractionListener.blocksFenceGate(
                Action.RIGHT_CLICK_BLOCK, Material.OAK_FENCE_GATE, true, true
        ));
    }
}
