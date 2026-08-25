package woo.siegePlugin.capture;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface CaptureSessionStatus {

    boolean isActiveParticipant(Player player);

    /**
     * Stage 4.4f replaces this stub with the real capture-session tracker.
     */
    static CaptureSessionStatus noActiveSessions() {
        return player -> false;
    }
}
