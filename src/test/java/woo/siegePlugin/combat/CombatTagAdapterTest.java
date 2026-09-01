package woo.siegePlugin.combat;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTagAdapterTest {

    @Test
    void activeTagMapIsTheCombatStateAuthority() {
        UUID tagged = UUID.randomUUID();
        UUID untagged = UUID.randomUUID();

        assertTrue(CombatTagAdapter.isTagged(Map.of(tagged, 1L), tagged));
        assertFalse(CombatTagAdapter.isTagged(Map.of(tagged, 1L), untagged));
    }
}
