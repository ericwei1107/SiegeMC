package woo.siegePlugin.lobby;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LobbyJoinItemTest {

    @Test
    void joinCompassUsesTheFourthHotbarSlot() {
        assertEquals(3, LobbyJoinItem.HOTBAR_SLOT);
    }

    @Test
    void joinCompassRunsBeforeProtectionAndHandlesCancelledInteractions() throws NoSuchMethodException {
        EventHandler handler = LobbyJoinItemListener.class
                .getMethod("onUse", PlayerInteractEvent.class)
                .getAnnotation(EventHandler.class);

        assertEquals(EventPriority.LOWEST, handler.priority());
        assertFalse(handler.ignoreCancelled());
    }
}
