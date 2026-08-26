package woo.siegePlugin.kit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitEditSessionStoreTest {

    @Test
    void closeDeathAndDisconnectEachHandOffTheDraftExactlyOnce() {
        for (KitEditSessionStore.EndCause cause : KitEditSessionStore.EndCause.values()) {
            KitEditSessionStore sessions = new KitEditSessionStore();
            UUID playerId = UUID.randomUUID();
            KitLoadout draft = KitLoadout.empty();
            draft.setItemAt(0, null);
            sessions.start(playerId, draft);

            KitEditSessionStore.Session finished = sessions.finish(playerId, cause).orElseThrow();

            assertEquals(draft, finished.loadout(), cause + " lost the virtual draft");
            assertFalse(sessions.finish(playerId, cause).isPresent(), cause + " finalized the draft twice");
        }
    }
}
