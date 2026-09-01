package woo.siegePlugin.lobby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialBookTest {

    @Test
    void tutorialOnlyOpensForFirstJoin() {
        assertTrue(TutorialBookListener.shouldOpen(false));
        assertFalse(TutorialBookListener.shouldOpen(true));
    }
}
