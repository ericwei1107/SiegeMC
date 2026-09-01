package woo.siegePlugin.lobby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LobbyJoinItemTest {

    @Test
    void joinCompassUsesTheFourthHotbarSlot() {
        assertEquals(3, LobbyJoinItem.HOTBAR_SLOT);
    }
}
