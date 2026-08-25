package woo.siegePlugin.capture;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import woo.siegePlugin.state.event.PlayerEnterSpectatorEvent;
import woo.siegePlugin.state.event.SiegeToLobbyEvent;

/**
 * Drops capture progress and controller credit when a player leaves the siege
 * context. Team exits are handled by the switch itself.
 */
public final class CaptureListener implements Listener {

    private final CaptureService captureService;

    public CaptureListener(CaptureService captureService) {
        this.captureService = captureService;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        captureService.clearParticipation(event.getPlayer());
    }

    @EventHandler
    public void onReturnToLobby(SiegeToLobbyEvent event) {
        captureService.clearParticipation(event.getPlayer());
    }

    @EventHandler
    public void onEnterSpectator(PlayerEnterSpectatorEvent event) {
        captureService.clearParticipation(event.getPlayer());
    }
}
