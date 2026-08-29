package woo.siegePlugin.round;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import woo.siegePlugin.state.PlayerStateTransitionService;
import woo.siegePlugin.team.Team;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Paper-backed messaging and player-movement port for the coordinator. */
public final class BukkitRoundAudience implements RoundAudience {

    private final Server server;
    private final PlayerStateTransitionService transitions;

    public BukkitRoundAudience(Server server, PlayerStateTransitionService transitions) {
        this.server = Objects.requireNonNull(server, "server");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    @Override
    public void broadcast(Component message) {
        server.broadcast(message);
    }

    @Override
    public void message(UUID playerId, Component message) {
        Player player = server.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(message);
        }
    }

    @Override
    public void actionBar(UUID playerId, Component message) {
        Player player = server.getPlayer(playerId);
        if (player != null) {
            player.sendActionBar(message);
        }
    }

    @Override
    public boolean isOnline(UUID playerId) {
        Player player = server.getPlayer(playerId);
        return player != null && player.isOnline();
    }

    @Override
    public String nameOf(UUID playerId) {
        Player player = server.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        String offlineName = server.getOfflinePlayer(playerId).getName();
        return offlineName == null ? playerId.toString() : offlineName;
    }

    @Override
    public String worldOf(UUID playerId) {
        Player player = server.getPlayer(playerId);
        return player == null ? null : player.getWorld().getName();
    }

    @Override
    public List<UUID> onlinePlayers() {
        return server.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
    }

    @Override
    public boolean sendToLobby(UUID playerId) {
        Player player = server.getPlayer(playerId);
        return player != null && transitions.forceRoundLobby(player);
    }

    @Override
    public boolean launchFighter(UUID playerId, Team team, ActiveRoundContext context) {
        Player player = server.getPlayer(playerId);
        return player != null && transitions.startFreshRound(player, team, context.spawn(team));
    }

    @Override
    public boolean launchSpectator(UUID playerId, ActiveRoundContext context) {
        Player player = server.getPlayer(playerId);
        return player != null && transitions.startFreshSpectatorRound(player, context.capturePoint());
    }

    @Override
    public void discardStoredRoundInventory(UUID playerId) {
        transitions.discardStoredRoundInventory(playerId);
    }

    @Override
    public boolean isInLobby(UUID playerId) {
        Player player = server.getPlayer(playerId);
        return player != null && transitions.isInLobbyContext(player);
    }
}
