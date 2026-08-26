package woo.siegePlugin.persistence;

/**
 * Why a score changed. Every ledger row carries one, so the running totals can
 * always be reconstructed from the audit trail.
 */
public enum ScoreReason {

    BANNER_CONTROL("banner_control", true),
    ENEMY_DEATH_BONUS("enemy_death_bonus", false),
    ADMIN_RESET("admin_reset", false);

    private final String storedValue;
    private final boolean contributesToSessionPoints;

    ScoreReason(String storedValue, boolean contributesToSessionPoints) {
        this.storedValue = storedValue;
        this.contributesToSessionPoints = contributesToSessionPoints;
    }

    public String storedValue() {
        return storedValue;
    }

    public boolean contributesToSessionPoints() {
        return contributesToSessionPoints;
    }
}
