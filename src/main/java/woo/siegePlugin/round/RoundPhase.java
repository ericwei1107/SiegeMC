package woo.siegePlugin.round;

/** Durable lifecycle phase owned exclusively by the rotation coordinator. */
public enum RoundPhase {
    BOOTSTRAPPING,
    ACTIVE,
    COMPLETING,
    INTERMISSION,
    ACTIVATING,
    RECOVERY
}
