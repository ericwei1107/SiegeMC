package woo.siegePlugin.death;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import woo.siegePlugin.economy.CurrencyService;
import woo.siegePlugin.round.ActiveCombatEligibility;
import woo.siegePlugin.score.ScoringService;
import woo.siegePlugin.state.PlayerStateTransitionService;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TownyAdapter;
import woo.siegePlugin.stats.MatchStatsTracker;

/**
 * The single death handler for siege rewards; keeping one listener means score,
 * currency, and kill MVP always agree on whether a death qualified.
 *
 * <p>Only an opposing battlefield fighter's kill credits anything. Every other
 * eligible siege death — environmental, self, friendly, or one whose score write
 * was rejected — receives exactly one announcement showing a zero-point change,
 * so the chat log never implies points were awarded when they were not.</p>
 */
public final class SiegeDeathListener implements Listener {

    private final TownyAdapter townyAdapter;
    private final ScoringService scoringService;
    private final CurrencyService currencyService;
    private final ActiveCombatEligibility eligibility;
    private final PlayerStateTransitionService playerStateTransitions;
    private final MatchStatsTracker statsTracker;

    public SiegeDeathListener(
            TownyAdapter townyAdapter,
            ScoringService scoringService,
            CurrencyService currencyService,
            ActiveCombatEligibility eligibility,
            PlayerStateTransitionService playerStateTransitions,
            MatchStatsTracker statsTracker
    ) {
        this.townyAdapter = townyAdapter;
        this.scoringService = scoringService;
        this.currencyService = currencyService;
        this.eligibility = eligibility;
        this.playerStateTransitions = playerStateTransitions;
        this.statsTracker = statsTracker;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clearSiegeDeathDrops(PlayerDeathEvent event) {
        if (playerStateTransitions.isInSiegeContext(event.getEntity())) {
            event.getDrops().clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        // Lobby, spectator, other-world, and unrostered deaths are not siege
        // deaths at all: no announcement, no score, no currency.
        if (!eligibility.isEligibleFighter(victim)) {
            return;
        }
        String matchId = eligibility.activeMatchId().orElse(null);
        Team victimTeam = townyAdapter.getPlayerTeam(victim).orElse(null);
        if (matchId == null || victimTeam == null) {
            return;
        }

        Player killer = victim.getKiller();
        Team killerTeam = killer == null ? null : townyAdapter.getPlayerTeam(killer).orElse(null);
        boolean creditableKill = killer != null
                && !killer.equals(victim)
                && killerTeam == victimTeam.opponent()
                && eligibility.isEligibleFighter(killer);
        if (!creditableKill) {
            victim.getServer().broadcast(SiegeDeathMessage.died(victimTeam, victim.getName(), 0L));
            return;
        }

        statsTracker.recordKill(matchId, killer.getUniqueId(), killer.getName());
        String killerName = killer.getName();
        scoringService.awardEnemyDeathBonus(killerTeam, accepted -> {
            if (!accepted) {
                // The round closed underneath this kill. Undo the MVP credit and
                // still announce once, honestly showing no points were awarded.
                statsTracker.rollbackKill(matchId, killer.getUniqueId());
                victim.getServer().broadcast(SiegeDeathMessage.killedByPlayer(
                        victimTeam, victim.getName(), killerName, 0L
                ));
                return;
            }
            victim.getServer().broadcast(SiegeDeathMessage.killedByPlayer(
                    victimTeam,
                    victim.getName(),
                    killerName,
                    scoringService.killRewardPoints()
            ));
            currencyService.awardKill(killer);
        });
    }
}
