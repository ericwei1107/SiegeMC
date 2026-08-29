package woo.siegePlugin.round;

import woo.siegePlugin.map.SiegeMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Pure candidate ordering: selected/non-repeat pool, then previous map last. */
public final class RotationOrder {

    private RotationOrder() {
    }

    public static List<SiegeMap> candidates(
            List<SiegeMap> enabled,
            String previousMapId,
            String requestedMapId,
            Random random
    ) {
        List<SiegeMap> pool = new ArrayList<>(enabled);
        if (requestedMapId != null) {
            SiegeMap requested = pool.stream().filter(map -> map.id().equals(requestedMapId)).findFirst().orElse(null);
            if (requested == null) return List.of();
            pool.remove(requested);
            Collections.shuffle(pool, random);
            pool.addFirst(requested);
            return List.copyOf(pool);
        }
        SiegeMap previous = pool.stream().filter(map -> Objects.equals(map.id(), previousMapId))
                .findFirst().orElse(null);
        if (previous != null) pool.remove(previous);
        Collections.shuffle(pool, random);
        if (previous != null) pool.add(previous);
        return List.copyOf(pool);
    }
}
