package woo.siegePlugin.round;

import woo.siegePlugin.team.Team;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Main-thread mirror of the durable roster for the currently published match.
 *
 * <p>Kept in memory because eligibility is asked on every damage event and
 * capture tick, which must never touch the database. The durable table remains
 * the source of truth: this mirror is rebuilt from it on activation and on
 * restart recovery, and every mutation here is paired with a durable write.</p>
 */
public final class RoundRoster {

    private final Map<UUID, Membership> members = new LinkedHashMap<>();
    private String matchId;

    public void bind(String matchId, List<Membership> memberships) {
        this.matchId = matchId;
        members.clear();
        memberships.forEach(value -> members.put(value.playerId(), value));
    }

    public void clear() {
        matchId = null;
        members.clear();
    }

    public String matchId() {
        return matchId;
    }

    public Optional<Membership> presenceOf(UUID playerId) {
        return Optional.ofNullable(members.get(playerId));
    }

    public void put(Membership membership) {
        members.put(membership.playerId(), membership);
    }

    public void remove(UUID playerId) {
        members.remove(playerId);
    }

    public void setPresence(UUID playerId, RosterPresence presence) {
        Membership current = members.get(playerId);
        if (current != null) {
            members.put(playerId, current.withPresence(presence));
        }
    }

    /** Online battlefield fighters per side, used for balancing and switches. */
    public int battlefieldFighterCount(Team team, java.util.function.Predicate<UUID> online) {
        return (int) members.values().stream()
                .filter(value -> value.role() == RoundRole.PLAYER)
                .filter(value -> value.presence() == RosterPresence.BATTLEFIELD)
                .filter(value -> value.team() == team)
                .filter(value -> online.test(value.playerId()))
                .count();
    }

    /** The side with fewer online battlefield fighters; RED on a tie, matching join balancing. */
    public Team smallerActiveSide(java.util.function.Predicate<UUID> online) {
        return battlefieldFighterCount(Team.RED, online) <= battlefieldFighterCount(Team.BLUE, online)
                ? Team.RED
                : Team.BLUE;
    }

    public List<Membership> all() {
        return List.copyOf(members.values());
    }

    public record Membership(UUID playerId, String playerName, Team team, RoundRole role, RosterPresence presence) {
        public Membership withPresence(RosterPresence next) {
            return new Membership(playerId, playerName, team, role, next);
        }
    }
}
