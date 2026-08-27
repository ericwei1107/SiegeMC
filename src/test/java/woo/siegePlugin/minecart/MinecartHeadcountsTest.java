package woo.siegePlugin.minecart;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecartHeadcountsTest {

    @Test
    void resolvesBothSidesWithoutDirectionAmbiguity() {
        MinecartHeadcounts headcounts = new MinecartHeadcounts(10, 3);

        assertEquals(10, headcounts.forTeam(Team.RED));
        assertEquals(3, headcounts.against(Team.RED));
        assertEquals(3, headcounts.forTeam(Team.BLUE));
        assertEquals(10, headcounts.against(Team.BLUE));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> new MinecartHeadcounts(-1, 0));
    }
}
