package woo.siegePlugin.kit;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitEditSessionStoreTest {

    @Test
    void newSessionReplacesTheOldGenerationAndFinishIsExactlyOnce() {
        KitEditSessionStore sessions = new KitEditSessionStore();
        UUID playerId = UUID.randomUUID();
        KitEditSessionStore.Session first = sessions.start(playerId, KitSelection.empty());
        KitEditSessionStore.Session second = sessions.start(playerId, new KitSelection(Map.of(2, "food")));

        assertTrue(second.generation() > first.generation());
        assertNull(sessions.get(playerId, first.generation()));
        assertEquals(second, sessions.finish(playerId));
        assertNull(sessions.finish(playerId));
    }

    @Test
    void intentionalNavigationSuppressesOnlyItsOwnCloseEvent() {
        KitEditSessionStore.Session session = new KitEditSessionStore()
                .start(UUID.randomUUID(), KitSelection.empty());

        session.navigate(KitEditSessionStore.View.CHOICE, 2);

        assertEquals(KitEditSessionStore.View.CHOICE, session.view());
        assertEquals(2, session.choiceSlot());
        assertTrue(session.consumeSuppressedClose());
        assertFalse(session.consumeSuppressedClose());
    }

    @Test
    void savingCanStartOnlyOnceAndTracksAnUnexpectedClose() {
        KitEditSessionStore.Session session = new KitEditSessionStore()
                .start(UUID.randomUUID(), KitSelection.empty());

        assertTrue(session.beginSaving());
        assertFalse(session.beginSaving());
        assertEquals(KitEditSessionStore.View.SAVING, session.view());
        assertTrue(session.consumeSuppressedClose());
        session.markClosedWhileSaving();
        assertTrue(session.closedWhileSaving());
    }
}
