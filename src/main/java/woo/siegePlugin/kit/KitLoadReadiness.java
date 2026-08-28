package woo.siegePlugin.kit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-thread state for asynchronous kit reads.
 *
 * <p>Each load receives a monotonically increasing token. A late completion
 * can only publish its result when it still owns the player's current token,
 * which prevents an old join from replacing a newer load (or a later saved
 * draft) after reconnect.</p>
 */
final class KitLoadReadiness {

    private final Map<UUID, Entry> entries = new HashMap<>();
    private long nextToken;

    long begin(UUID playerId) {
        long token = ++nextToken;
        entries.put(playerId, new Entry(token, State.LOADING));
        return token;
    }

    boolean complete(UUID playerId, long token) {
        return transition(playerId, token, State.READY);
    }

    boolean fail(UUID playerId, long token) {
        return transition(playerId, token, State.FAILED);
    }

    boolean isReady(UUID playerId) {
        Entry entry = entries.get(playerId);
        return entry != null && entry.state() == State.READY;
    }

    boolean isFailed(UUID playerId) {
        Entry entry = entries.get(playerId);
        return entry != null && entry.state() == State.FAILED;
    }

    void forget(UUID playerId) {
        entries.remove(playerId);
    }

    private boolean transition(UUID playerId, long token, State state) {
        Entry current = entries.get(playerId);
        if (current == null || current.token() != token) {
            return false;
        }
        entries.put(playerId, new Entry(token, state));
        return true;
    }

    private record Entry(long token, State state) {
    }

    private enum State {
        LOADING,
        READY,
        FAILED
    }
}
