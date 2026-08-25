package woo.siegePlugin.state;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import woo.siegePlugin.state.event.LobbyToSiegeEvent;
import woo.siegePlugin.state.event.PlayerEnterSpectatorEvent;
import woo.siegePlugin.state.event.PlayerExitSpectatorEvent;
import woo.siegePlugin.state.event.SiegeToLobbyEvent;

import java.util.Objects;

/**
 * Entry point for commands and game systems that change a player's context.
 */
public final class PlayerStateTransitions {

    private final Server server;

    public PlayerStateTransitions(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public void enterSiegeFromLobby(Player player) {
        server.getPluginManager().callEvent(new LobbyToSiegeEvent(player));
    }

    public void returnToLobby(Player player) {
        server.getPluginManager().callEvent(new SiegeToLobbyEvent(player));
    }

    public void enterSpectator(Player player) {
        server.getPluginManager().callEvent(new PlayerEnterSpectatorEvent(player));
    }

    public void exitSpectator(Player player) {
        server.getPluginManager().callEvent(new PlayerExitSpectatorEvent(player));
    }
}
