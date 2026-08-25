package woo.siegePlugin.capture;

import org.bukkit.entity.Player;

public interface CaptureSessionStatus {

    boolean isActiveParticipant(Player player);

    /** Drops any in-progress session and completed controller credit. */
    void clearParticipation(Player player);
}
