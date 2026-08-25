package woo.siegePlugin.arena;

/**
 * Tracking of player-placed battlefield blocks. Stage 4.4i.1 supplies the real
 * implementation; resets only need to know how to forget everything.
 */
@FunctionalInterface
public interface PlacedBlockTracker {

    void clearAll();

    static PlacedBlockTracker notTrackingYet() {
        return () -> {
        };
    }
}
