package woo.siegePlugin.persistence;

/**
 * Why a score changed. Every ledger row carries one, so the running totals can
 * always be reconstructed from the audit trail.
 */
public enum ScoreReason {

    BANNER_CONTROL("banner_control"),
    ENEMY_DEATH_BONUS("enemy_death_bonus"),
    ADMIN_RESET("admin_reset");

    private final String storedValue;

    ScoreReason(String storedValue) {
        this.storedValue = storedValue;
    }

    public String storedValue() {
        return storedValue;
    }
}
