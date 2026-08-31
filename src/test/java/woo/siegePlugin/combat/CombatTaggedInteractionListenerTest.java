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
                Action.RIGHT_CLICK_BLOCK, Material.OAK_FENCE_GATE, true
        ));
        assertTrue(CombatTaggedInteractionListener.blocksFenceGate(
                Action.RIGHT_CLICK_BLOCK, Material.WARPED_FENCE_GATE, true
        ));
    }

    @Test
    void untaggedPlayersAndOtherInteractionsRemainAllowed() {
        assertFalse(CombatTaggedInteractionListener.blocksFenceGate(
                Action.RIGHT_CLICK_BLOCK, Material.OAK_FENCE_GATE, false
        ));
        assertFalse(CombatTaggedInteractionListener.blocksFenceGate(
                Action.RIGHT_CLICK_BLOCK, Material.OAK_DOOR, true
        ));
        assertFalse(CombatTaggedInteractionListener.blocksFenceGate(
                Action.LEFT_CLICK_BLOCK, Material.OAK_FENCE_GATE, true
        ));
    }
}
