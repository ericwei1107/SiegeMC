package woo.siegePlugin.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveWorldNameTest {

    @Test
    void generatedNamesAreUniqueAndSafeToIdentifyAsActiveCopies() {
        SiegeMap map = map("al_quds");

        String first = ActiveWorldName.next(map);
        String second = ActiveWorldName.next(map);

        assertTrue(first.startsWith("siege-active-"));
        assertTrue(first.endsWith("-al_quds"));
        assertNotEquals(first, second);
    }

    @Test
    void unsafeMapIdsCannotBecomeFolderNames() {
        assertThrows(IllegalArgumentException.class, () -> ActiveWorldName.next(map("../outside")));
    }

    private static SiegeMap map(String id) {
        MapPoint point = new MapPoint(0, 64, 0, 0, 0);
        return new SiegeMap(id, "Map", "template", point, point, point, 16, new MapBounds(-10, -10, 10, 10));
    }
}
