package woo.siegePlugin.minecart;

/** Pure decision logic for configurable siege TNT-minecart entity budgets. */
public final class MinecartPlacementLimits {

    public enum Outcome {
        ALLOWED,
        PLAYER_CAP_REACHED,
        ARENA_CAP_REACHED
    }

    private MinecartPlacementLimits() {
    }

    public static Outcome evaluate(int playerActive, int arenaActive, MinecartSettings settings) {
        if (settings.maxActivePerPlayer() > 0 && playerActive >= settings.maxActivePerPlayer()) {
            return Outcome.PLAYER_CAP_REACHED;
        }
        if (settings.maxActiveArena() > 0 && arenaActive >= settings.maxActiveArena()) {
            return Outcome.ARENA_CAP_REACHED;
        }
        return Outcome.ALLOWED;
    }
}
