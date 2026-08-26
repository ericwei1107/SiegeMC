package woo.siegePlugin.kit;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Holds virtual kit-editor drafts until one terminal lifecycle event saves it. */
final class KitEditSessionStore {

    enum EndCause {
        INVENTORY_CLOSE,
        DEATH,
        DISCONNECT
    }

    private final Map<UUID, Session> sessions = new HashMap<>();

    Session start(UUID playerId, KitLoadout loadout) {
        Session session = new Session(loadout);
        sessions.put(playerId, session);
        return session;
    }

    Optional<Session> finish(UUID playerId, EndCause cause) {
        // The cause documents the three Bukkit lifecycle paths. All must hand
        // the same virtual draft to persistence exactly once.
        return Optional.ofNullable(sessions.remove(playerId));
    }

    Session get(UUID playerId) {
        return sessions.get(playerId);
    }

    static final class Session {

        private KitLoadout loadout;
        private String selected;

        private Session(KitLoadout loadout) {
            this.loadout = loadout;
        }

        KitLoadout loadout() {
            return loadout;
        }

        void setLoadout(KitLoadout loadout) {
            this.loadout = loadout;
        }

        String selected() {
            return selected;
        }

        void setSelected(String selected) {
            this.selected = selected;
        }
    }
}
