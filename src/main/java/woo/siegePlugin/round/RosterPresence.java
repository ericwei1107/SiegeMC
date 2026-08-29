package woo.siegePlugin.round;

/**
 * Where a rostered player currently is, as the durable roster sees it.
 *
 * <p>This is what makes the roster — not Towny residency — authoritative for
 * eligibility. Towny only says which container a player belongs to; it cannot
 * distinguish a fighter standing on the battlefield from the same fighter
 * waiting in the lobby between rounds.</p>
 */
public enum RosterPresence {
    /** Assigned during activation but not yet confirmed on the battlefield. */
    PLANNED,
    /** Launched into the active map; eligible for scoring, capture, and rewards. */
    BATTLEFIELD,
    /** Voluntarily returned to the lobby; must run /siege join to come back. */
    LOBBY
}
