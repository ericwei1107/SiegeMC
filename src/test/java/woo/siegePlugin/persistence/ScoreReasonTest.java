package woo.siegePlugin.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreReasonTest {

    @Test
    void onlyBannerControlContributesToCurrentBatSessionPoints() {
        assertTrue(ScoreReason.BANNER_CONTROL.contributesToSessionPoints());
        assertFalse(ScoreReason.ENEMY_DEATH_BONUS.contributesToSessionPoints());
        assertFalse(ScoreReason.ADMIN_RESET.contributesToSessionPoints());
    }
}
