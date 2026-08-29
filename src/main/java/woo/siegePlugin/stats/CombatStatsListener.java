package woo.siegePlugin.stats;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import woo.siegePlugin.round.ActiveCombatEligibility;
import woo.siegePlugin.minecart.SiegeMinecartMarker;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TownyAdapter;

/** Records capped, post-mitigation enemy-player damage for MVP calculation. */
public final class CombatStatsListener implements Listener {

    private final TownyAdapter towny;
    private final SiegeMinecartMarker minecartMarker;
    private final ActiveCombatEligibility eligibility;
    private final MatchStatsTracker stats;

    public CombatStatsListener(
            TownyAdapter towny,
            SiegeMinecartMarker minecartMarker,
            ActiveCombatEligibility eligibility,
            MatchStatsTracker stats
    ) {
        this.towny = towny;
        this.minecartMarker = minecartMarker;
        this.eligibility = eligibility;
        this.stats = stats;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        // Both ends must be battlefield fighters in the active round: a duel in
        // the lobby, or damage dealt by someone who already left, must not
        // register toward the Damage MVP.
        if (attacker == null || attacker.equals(victim)
                || !eligibility.isEligibleFighter(victim)
                || !eligibility.isEligibleFighter(attacker)) {
            return;
        }
        String matchId = eligibility.activeMatchId().orElse(null);
        Team victimTeam = towny.getPlayerTeam(victim).orElse(null);
        Team attackerTeam = towny.getPlayerTeam(attacker).orElse(null);
        if (matchId == null || victimTeam == null || attackerTeam != victimTeam.opponent()) {
            return;
        }
        double remaining = Math.max(0D, victim.getHealth() + victim.getAbsorptionAmount());
        double applied = Math.min(Math.max(0D, event.getFinalDamage()), remaining);
        stats.recordDamage(matchId, attacker.getUniqueId(), attacker.getName(), applied);
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        if (damager instanceof ExplosiveMinecart minecart) {
            return minecartMarker.ownerOf(minecart)
                    .map(org.bukkit.Bukkit::getPlayer)
                    .orElse(null);
        }
        return null;
    }
}
