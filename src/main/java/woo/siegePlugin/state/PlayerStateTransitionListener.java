package woo.siegePlugin.state;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import woo.siegePlugin.round.ActiveCombatEligibility;
import woo.siegePlugin.state.event.LobbyToSiegeEvent;
import woo.siegePlugin.state.event.PlayerEnterSpectatorEvent;
import woo.siegePlugin.state.event.PlayerExitSpectatorEvent;
import woo.siegePlugin.state.event.SiegeToLobbyEvent;
import woo.siegePlugin.team.TeamSpawnLocations;

import java.util.Objects;

public final class PlayerStateTransitionListener implements Listener {

    private final PlayerStateTransitionService transitions;
    private final ActiveCombatEligibility eligibility;
    private final TeamSpawnLocations teamSpawns;

    public PlayerStateTransitionListener(
            PlayerStateTransitionService transitions,
            ActiveCombatEligibility eligibility,
            TeamSpawnLocations teamSpawns
    ) {
        this.transitions = Objects.requireNonNull(transitions, "transitions");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.teamSpawns = Objects.requireNonNull(teamSpawns, "teamSpawns");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        transitions.handleJoin(event.getPlayer());
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        eligibility.fighterTeam(event.getPlayer()).ifPresent(team -> event.setRespawnLocation(teamSpawns.get(team)));
        transitions.reapplyKitAfterRespawn(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        transitions.handleQuit(event.getPlayer());
    }
}
