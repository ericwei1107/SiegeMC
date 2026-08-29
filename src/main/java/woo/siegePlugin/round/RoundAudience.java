package woo.siegePlugin.round;

import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * Everything the coordinator needs to say to players, and everything it needs
 * to move them, behind one port.
 *
 * <p>Separating this from the Bukkit implementation lets the lifecycle tests
 * assert "this player was launched onto BLUE and that one was returned to the
 * lobby" without a running server, and lets a launch failure be simulated
 * exactly the way a blocked teleport behaves in production.</p>
 */
public interface RoundAudience {

    void broadcast(Component message);

    void message(UUID playerId, Component message);

    void actionBar(UUID playerId, Component message);

    boolean isOnline(UUID playerId);

    /** Display name for a possibly-offline player; falls back to the UUID. */
    String nameOf(UUID playerId);

    /** The world a player is currently in, or null when offline. */
    String worldOf(UUID playerId);

    java.util.List<UUID> onlinePlayers();

    /** Evacuates to the Adventure-mode lobby, discarding round inventory. */
    boolean sendToLobby(UUID playerId);

    /** Launches a fighter: Towny container, spawn, Survival, fresh kit. */
    boolean launchFighter(UUID playerId, woo.siegePlugin.team.Team team, ActiveRoundContext context);

    /** Launches a spectator into the battlefield in Spectator mode. */
    boolean launchSpectator(UUID playerId, ActiveRoundContext context);

    /** Forgets a player's stored round inventory so nothing carries over. */
    void discardStoredRoundInventory(UUID playerId);

    /** True when the player is currently in the lobby context. */
    boolean isInLobby(UUID playerId);
}
