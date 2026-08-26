package woo.siegePlugin.minecart;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecartPlacementListenerTest {

    @Test
    void convertsTheApprovedThirtySecondCooldownToMinecraftTicks() {
        assertEquals(600, MinecartPlacementListener.cooldownTicks(Duration.ofSeconds(30)));
        assertEquals(0, MinecartPlacementListener.cooldownTicks(Duration.ZERO));
    }

    @Test
    void rejectsCooldownsThatMinecraftCannotRepresent() {
        assertThrows(IllegalArgumentException.class, () -> MinecartPlacementListener.cooldownTicks(Duration.ofSeconds(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> MinecartPlacementListener.cooldownTicks(Duration.ofSeconds(Long.MAX_VALUE))
        );
    }
}
