package woo.siegePlugin.state;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import woo.siegePlugin.state.event.LobbyToSiegeEvent;
import woo.siegePlugin.state.event.PlayerEnterSpectatorEvent;
import woo.siegePlugin.state.event.PlayerExitSpectatorEvent;
import woo.siegePlugin.state.event.SiegeToLobbyEvent;

import java.util.Objects;

public final class PlayerStateTransitionListener implements Listener {

    private final PlayerStateTransitionService transitions;

    public PlayerStateTransitionListener(PlayerStateTransitionService transitions) {
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    @EventHandler
    public void onLobbyToSiege(LobbyToSiegeEvent event) {
        transitions.enterSiegeFromLobby(event.getPlayer());
    }

    @EventHandler
    public void onSiegeToLobby(SiegeToLobbyEvent event) {
        transitions.returnToLobby(event.getPlayer());
    }

    @EventHandler
    public void onPlayerEnterSpectator(PlayerEnterSpectatorEvent event) {
        transitions.enterSpectator(event.getPlayer());
    }

    @EventHandler
    public void onPlayerExitSpectator(PlayerExitSpectatorEvent event) {
        transitions.exitSpectator(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        transitions.reapplyKitAfterRespawn(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        transitions.handleQuit(event.getPlayer());
    }
}
