package woo.siegePlugin.round;

import woo.siegePlugin.team.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Randomizes each launch and guarantees competitive sides differ by at most one. */
public final class RosterBalancer {

    private RosterBalancer() {
    }

    /**
     * Shuffles the roster, then alternates sides starting from a randomly chosen
     * team. Alternating from a fixed team would hand the odd player to that side
     * every single round, so the starting side is randomized too.
     *
     * @return assignments in launch order, so a caller applying them one at a
     *         time keeps the successful sides balanced
     */
    public static List<Assignment> balance(List<UUID> playerIds, Random random) {
        List<UUID> shuffled = new ArrayList<>(playerIds);
        Collections.shuffle(shuffled, random);
        Team first = random.nextBoolean() ? Team.RED : Team.BLUE;
        List<Assignment> assignments = new ArrayList<>(shuffled.size());
        for (int index = 0; index < shuffled.size(); index++) {
            assignments.add(new Assignment(shuffled.get(index), index % 2 == 0 ? first : first.opponent()));
        }
        return List.copyOf(assignments);
    }

    /** The side with fewer successful launches so far; ties keep the planned side. */
    public static Team smallerSide(int redCount, int blueCount, Team planned) {
        if (redCount < blueCount) {
            return Team.RED;
        }
        if (blueCount < redCount) {
            return Team.BLUE;
        }
        return planned;
    }

    public static Map<Team, Integer> counts(List<Assignment> assignments) {
        Map<Team, Integer> counts = new EnumMap<>(Team.class);
        counts.put(Team.RED, 0);
        counts.put(Team.BLUE, 0);
        assignments.forEach(value -> counts.compute(value.team(), (ignored, count) -> count + 1));
        return counts;
    }

    public record Assignment(UUID playerId, Team team) {
    }
}
