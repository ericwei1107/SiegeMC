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

    /**
     * Stage 4.4h.1 replaces this with the real timed cycle. Until then the
     * server behaves exactly as that stage's boot state: permanently ACTIVE.
     */
    static SiegePhaseStatus alwaysActive() {
        return () -> SiegePhase.ACTIVE;
    }
}
