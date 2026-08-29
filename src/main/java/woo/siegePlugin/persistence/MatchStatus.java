package woo.siegePlugin.persistence;

/** Lifecycle state for a durable match record. */
public enum MatchStatus {
    LEGACY,
    ACTIVE,
    COMPLETED,
    ABORTED
}
