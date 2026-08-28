package woo.siegePlugin.kit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread state for one temporary kit-editing draft per player. */
final class KitEditSessionStore {

    enum View {
        EDITOR,
        CHOICE,
        SAVING
    }

    private final Map<UUID, Session> sessions = new HashMap<>();
    private long nextGeneration;

    Session start(UUID playerId, KitSelection selection) {
        Session session = new Session(++nextGeneration, selection);
        sessions.put(playerId, session);
        return session;
    }

    Session get(UUID playerId) {
        return sessions.get(playerId);
    }

    Session get(UUID playerId, long generation) {
        Session session = sessions.get(playerId);
        return session != null && session.generation() == generation ? session : null;
    }

    Session finish(UUID playerId) {
        return sessions.remove(playerId);
    }

    void clear() {
        sessions.clear();
    }

    static final class Session {

        private final long generation;
        private KitSelection selection;
        private View view = View.EDITOR;
        private int choiceSlot = -1;
        private boolean suppressNextClose;
        private boolean closedWhileSaving;

        private Session(long generation, KitSelection selection) {
            this.generation = generation;
            this.selection = selection;
        }

        long generation() {
            return generation;
        }

        KitSelection selection() {
            return selection;
        }

        void setSelection(KitSelection selection) {
            this.selection = selection;
        }

        View view() {
            return view;
        }

        int choiceSlot() {
            return choiceSlot;
        }

        void navigate(View view, int choiceSlot) {
            this.view = view;
            this.choiceSlot = choiceSlot;
            this.suppressNextClose = true;
        }

        boolean beginSaving() {
            if (view != View.EDITOR) {
                return false;
            }
            navigate(View.SAVING, -1);
            return true;
        }

        boolean consumeSuppressedClose() {
            if (!suppressNextClose) {
                return false;
            }
            suppressNextClose = false;
            return true;
        }

        void markClosedWhileSaving() {
            closedWhileSaving = true;
        }

        boolean closedWhileSaving() {
            return closedWhileSaving;
        }
    }
}
