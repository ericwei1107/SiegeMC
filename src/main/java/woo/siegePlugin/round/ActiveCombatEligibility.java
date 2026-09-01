package woo.siegePlugin.round;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import woo.siegePlugin.team.Team;

/**
 * The single rule deciding whether a player's activity counts toward the round.
 *
 * <p>Before this existed each system asked a slightly different question —
 * some only checked the phase, some only Towny residency — which let a player
 * standing in the lobby or in an unrelated world influence scores, currency,
 * capture progress, and MVP totals. Every one of those systems now asks here,
 * and all four conditions must hold:</p>
 *
 * <ol>
 *   <li>a round is published and {@code ACTIVE};</li>
 *   <li>the player is in that round's generated runtime world;</li>
 *   <li>the durable roster records them as {@link RosterPresence#BATTLEFIELD};</li>
 *   <li>they hold the role the caller requires.</li>
 * </ol>
 *
 * <p>The roster is authoritative rather than Towny because Towny cannot tell a
 * fighter on the battlefield apart from the same fighter waiting in the lobby.</p>
 */
public final class ActiveCombatEligibility {

    private final ActiveRoundProvider rounds;
    private final RoundRoster roster;

    public ActiveCombatEligibility(ActiveRoundProvider rounds, RoundRoster roster) {
        this.rounds = Objects.requireNonNull(rounds, "rounds");
        this.roster = Objects.requireNonNull(roster, "roster");
    }

    /** True when this player's combat currently counts as a fighter. */
    public boolean isEligibleFighter(Player player) {
        return fighterTeam(player).isPresent();
    }

    /** The authoritative active-round team for a battlefield fighter. */
    public Optional<Team> fighterTeam(Player player) {
        return entryFor(player)
                .filter(entry -> entry.role() == RoundRole.PLAYER)
                .map(RoundRoster.Membership::team);
    }

    /** True when this player is present on the battlefield in any role. */
    public boolean isOnBattlefield(Player player) {
        return entryFor(player).isPresent();
    }

    /** The active match ID, present only while a round is published and ACTIVE. */
    public Optional<String> activeMatchId() {
        return rounds.isActive() ? rounds.current().map(ActiveRoundContext::matchId) : Optional.empty();
    }

    /** True when a UUID is a battlefield fighter, for callers holding no Player. */
    public boolean isEligibleFighter(UUID playerId, String worldName) {
        ActiveRoundContext context = activeContext(worldName).orElse(null);
        if (context == null) {
            return false;
        }
        return roster.presenceOf(playerId)
                .filter(entry -> entry.presence() == RosterPresence.BATTLEFIELD)
                .filter(entry -> entry.role() == RoundRole.PLAYER)
                .isPresent();
    }

    private Optional<RoundRoster.Membership> entryFor(Player player) {
        if (activeContext(player.getWorld().getName()).isEmpty()) {
            return Optional.empty();
        }
        return roster.presenceOf(player.getUniqueId())
                .filter(entry -> entry.presence() == RosterPresence.BATTLEFIELD);
    }

    private Optional<ActiveRoundContext> activeContext(String worldName) {
        if (!rounds.isActive()) {
            return Optional.empty();
        }
        return rounds.current().filter(context -> context.world().getName().equals(worldName));
    }
}
