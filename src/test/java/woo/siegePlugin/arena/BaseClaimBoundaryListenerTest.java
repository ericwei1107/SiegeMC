package woo.siegePlugin.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BaseClaimBoundaryListenerTest {

    @Test
    void onlyActualBoundaryCrossingsProducePrivateMessages() {
        assertEquals(Component.text("You have entered your claim", NamedTextColor.GREEN),
                BaseClaimBoundaryListener.transitionMessage(false, true));
        assertEquals(Component.text("You have exited your claim", NamedTextColor.RED),
                BaseClaimBoundaryListener.transitionMessage(true, false));
        assertNull(BaseClaimBoundaryListener.transitionMessage(true, true));
        assertNull(BaseClaimBoundaryListener.transitionMessage(false, false));
    }
}
