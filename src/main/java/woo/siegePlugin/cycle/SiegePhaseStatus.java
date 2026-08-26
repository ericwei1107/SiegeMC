package woo.siegePlugin.cycle;

/**
 * The current activity phase. Scoring and rewards only run while
 * {@link SiegePhase#ACTIVE}.
 */
@FunctionalInterface
public interface SiegePhaseStatus {

    SiegePhase currentPhase();

    default boolean isActive() {
        return currentPhase() == SiegePhase.ACTIVE;
    }

    /** Returns an ACTIVE status for isolated callers that do not own a cycle. */
    static SiegePhaseStatus alwaysActive() {
        return () -> SiegePhase.ACTIVE;
    }
}
