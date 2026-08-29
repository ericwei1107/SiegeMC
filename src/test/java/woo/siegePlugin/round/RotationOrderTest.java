package woo.siegePlugin.round;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.map.MapBounds;
import woo.siegePlugin.map.MapPoint;
import woo.siegePlugin.map.SiegeMap;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotationOrderTest {

    @Test
    void previousMapIsAlwaysTheLastCleanCopyFallback() {
        List<SiegeMap> order = RotationOrder.candidates(
                List.of(map("a"), map("b"), map("c")), "b", null, new Random(9L)
        );
        assertEquals("b", order.getLast().id());
        assertEquals(3, order.stream().map(SiegeMap::id).distinct().count());
    }

    @Test
    void requestedRecoveryMapIsTriedFirstAndEveryOtherMapRemainsAvailable() {
        List<SiegeMap> order = RotationOrder.candidates(
                List.of(map("a"), map("b"), map("c")), "b", "c", new Random(9L)
        );
        assertEquals("c", order.getFirst().id());
        assertEquals(3, order.size());
    }

    private static SiegeMap map(String id) {
        MapPoint point = new MapPoint(0, 64, 0, 0, 0);
        return new SiegeMap(id, id, id, point, point, point, 10, new MapBounds(-10, -10, 10, 10));
    }
}
