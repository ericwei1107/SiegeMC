package woo.siegePlugin.arena;

/**
 * Owns the single arena-maintenance lifecycle so snapshot capture and map
 * restoration can never touch the same world/files at the same time.
 *
 * <p>All transitions are initiated on the Bukkit server thread. The methods
 * are synchronized as an additional guard against future asynchronous callers.</p>
 */
public final class ArenaMaintenanceCoordinator {

    public enum State {
        IDLE,
        CAPTURING,
        RESET_COUNTDOWN,
        RESTORING
    }

    private State state = State.IDLE;

    public synchronized State state() {
        return state;
    }

    public synchronized boolean beginCapture() {
        return transition(State.IDLE, State.CAPTURING);
    }

    public synchronized void finishCapture() {
        release(State.CAPTURING);
    }

    public synchronized boolean beginResetCountdown() {
        return transition(State.IDLE, State.RESET_COUNTDOWN);
    }

    public synchronized boolean beginRestore() {
        return transition(State.RESET_COUNTDOWN, State.RESTORING);
    }

    public synchronized void finishReset() {
        if (state == State.RESET_COUNTDOWN || state == State.RESTORING) {
            state = State.IDLE;
        }
    }

    private boolean transition(State expected, State destination) {
        if (state != expected) {
            return false;
        }
        state = destination;
        return true;
    }

    private void release(State owner) {
        if (state == owner) {
            state = State.IDLE;
        }
    }
}
