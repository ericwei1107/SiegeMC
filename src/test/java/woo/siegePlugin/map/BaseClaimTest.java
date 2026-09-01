package woo.siegePlugin.map;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseClaimTest {

    @Test
    void nativeChunkAlignmentHandlesPositiveAndNegativeBoundaries() {
        BaseClaim zero = new BaseClaim(Team.RED, 0, 0);
        assertTrue(zero.containsBlock(0, 0));
        assertTrue(zero.containsBlock(15, 15));
        assertFalse(zero.containsBlock(16, 0));

        BaseClaim negativeOne = new BaseClaim(Team.BLUE, -1, -1);
        assertTrue(negativeOne.containsBlock(-1, -1));
        assertTrue(negativeOne.containsBlock(-16, -16));
        assertFalse(negativeOne.containsBlock(-17, -1));

        BaseClaim negativeTwo = new BaseClaim(Team.BLUE, -2, 0);
        assertTrue(negativeTwo.containsBlock(-17, 0));
        assertTrue(negativeTwo.containsBlock(-32, 15));
    }

    @Test
    void wholeChunkMustFitInsideArenaBounds() {
        assertTrue(new BaseClaim(Team.RED, 0, 0).fitsInside(new MapBounds(0, 0, 31, 31)));
        assertFalse(new BaseClaim(Team.RED, 1, 1).fitsInside(new MapBounds(0, 0, 30, 30)));
    }
}
