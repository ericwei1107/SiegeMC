package woo.siegePlugin.round;

import java.time.Instant;

/**
 * The coordinator's view of time and the server thread.
 *
 * <p>Injecting this is what lets the lifecycle be tested deterministically: a
 * test drives the readiness tick and the intermission clock directly instead of
 * waiting forty real seconds for a Bukkit task.</p>
 */
public interface RoundScheduler {

    /** Runs an action on the server thread, or immediately if already on it. */
    void onServerThread(Runnable action);

    /** Starts the one-second readiness/countdown tick, replacing any existing one. */
    void startTicking(Runnable tick);

    void stopTicking();

    Instant now();
}
