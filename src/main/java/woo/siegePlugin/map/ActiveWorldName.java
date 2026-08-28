package woo.siegePlugin.map;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Produces direct-child server-world folder names that CleanCopyDirectory may safely remove. */
final class ActiveWorldName {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private ActiveWorldName() {
    }

    static String next(SiegeMap map) {
        Objects.requireNonNull(map, "map");
        String id = map.id();
        if (!id.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Map id may contain only letters, numbers, underscores, and hyphens");
        }
        return "siege-active-" + System.currentTimeMillis() + "-" + SEQUENCE.incrementAndGet() + "-" + id;
    }
}
