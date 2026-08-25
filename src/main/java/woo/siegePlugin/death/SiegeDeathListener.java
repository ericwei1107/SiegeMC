package woo.siegePlugin.death;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import woo.siegePlugin.cycle.SiegePhaseStatus;
import woo.siegePlugin.economy.CurrencyService;
import woo.siegePlugin.score.ScoringService;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TownyAdapter;

/**
 * The single death handler for siege rewards. Stage 4.4j extends this with
 * killer currency; keeping one listener means both rewards always agree on
 * whether a death qualified.
 *
 * <p>Deliberately has no killer requirement and no location gate: a team
 * player dying anywhere, to anything, credits the other side.</p>
 */
public final class SiegeDeathListener implements Listener {

    private final TownyAdapter townyAdapter;
    private final ScoringService scoringService;
    private final CurrencyService currencyService;
    private final SiegePhaseStatus phaseStatus;

    public SiegeDeathListener(
            TownyAdapter townyAdapter,
            ScoringService scoringService,
            CurrencyService currencyService,
            SiegePhaseStatus phaseStatus
    ) {
        this.townyAdapter = townyAdapter;
        this.scoringService = scoringService;
        this.currencyService = currencyService;
        this.phaseStatus = phaseStatus;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // During BREAK a death earns neither score nor currency, so nothing
        // below this line — including the Towny lookup — needs to run.
        if (!phaseStatus.isActive()) {
            return;
        }

        Player victim = event.getEntity();
        Team victimTeam = townyAdapter.getPlayerTeam(victim).orElse(null);
        if (victimTeam == null) {
            return;
        }

        scoringService.awardEnemyDeathBonus(victimTeam.opponent());
        awardKillerCurrency(victim, victimTeam);
    }

    /**
     * Currency needs a killer, so environmental deaths pay nothing even though
     * they still moved the team score. Self-kills and team-kills pay nothing
     * either.
     */
    private void awardKillerCurrency(Player victim, Team victimTeam) {
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }

        Team killerTeam = townyAdapter.getPlayerTeam(killer).orElse(null);
        if (killerTeam != victimTeam.opponent()) {
            return;
        }

        currencyService.awardKill(killer);
    }
}
