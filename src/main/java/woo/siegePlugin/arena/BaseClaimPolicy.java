package woo.siegePlugin.arena;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import woo.siegePlugin.map.BaseClaim;
import woo.siegePlugin.round.ActiveCombatEligibility;
import woo.siegePlugin.round.ActiveRoundContext;
import woo.siegePlugin.round.ActiveRoundProvider;
import woo.siegePlugin.team.Team;

import java.util.Objects;
import java.util.Optional;

/** Generation-safe lookup boundary for the current map's team-owned base chunks. */
public final class BaseClaimPolicy {

    private final ActiveRoundProvider rounds;
    private final ActiveCombatEligibility eligibility;

    public BaseClaimPolicy(ActiveRoundProvider rounds, ActiveCombatEligibility eligibility) {
        this.rounds = Objects.requireNonNull(rounds, "rounds");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
    }

    public Optional<BaseClaim> claimAt(Block block) {
        return claimAt(block.getLocation());
    }

    public Optional<BaseClaim> claimAt(Location location) {
        ActiveRoundContext context = rounds.isActive() ? rounds.current().orElse(null) : null;
        if (context == null || location.getWorld() == null || !context.world().equals(location.getWorld())) {
            return Optional.empty();
        }
        return context.map().claimAt(location.getBlockX(), location.getBlockZ());
    }

    public Optional<Team> fighterTeam(Player player) {
        return eligibility.fighterTeam(player);
    }

    public boolean isOwnClaim(Player player, Location location) {
        Team team = fighterTeam(player).orElse(null);
        return team != null && claimAt(location).map(claim -> claim.team() == team).orElse(false);
    }
}
