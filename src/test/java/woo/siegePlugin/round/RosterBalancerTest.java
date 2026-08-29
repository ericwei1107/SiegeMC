package woo.siegePlugin.round;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RosterBalancerTest {

    @Test
    void everyRosterIsBalancedWithinOne() {
        List<UUID> players = players(9);

        List<RosterBalancer.Assignment> assignments = RosterBalancer.balance(players, new Random(7L));
        Map<Team, Integer> counts = RosterBalancer.counts(assignments);

        assertEquals(players.size(), assignments.size());
        assertEquals(players.size(), assignments.stream()
                .map(RosterBalancer.Assignment::playerId).distinct().count());
        assertEquals(1, Math.abs(counts.get(Team.RED) - counts.get(Team.BLUE)));
    }

    @Test
    void theOddPlayerDoesNotAlwaysLandOnTheSameSide() {
        Set<Team> majoritySides = new HashSet<>();
        List<UUID> players = players(5);
        for (long seed = 0L; seed < 20L; seed++) {
            Map<Team, Integer> counts = RosterBalancer.counts(
                    RosterBalancer.balance(players, new Random(seed))
            );
            majoritySides.add(counts.get(Team.RED) > counts.get(Team.BLUE) ? Team.RED : Team.BLUE);
        }
        assertEquals(2, majoritySides.size(), "the extra player must be able to land on either side");
    }

    @Test
    void anEvenRosterIsAlwaysSplitExactly() {
        for (long seed = 0L; seed < 20L; seed++) {
            Map<Team, Integer> counts = RosterBalancer.counts(
                    RosterBalancer.balance(players(6), new Random(seed))
            );
            assertEquals(counts.get(Team.RED), counts.get(Team.BLUE));
        }
    }

    @Test
    void aFailedLaunchIsCorrectedByAssigningTheNextPlayerToTheSmallerSuccessfulSide() {
        // Two Reds launched, one Blue failed: the next planned Blue still goes Blue.
        assertEquals(Team.BLUE, RosterBalancer.smallerSide(2, 1, Team.BLUE));
        // The planned side would deepen the gap, so the smaller side wins instead.
        assertEquals(Team.BLUE, RosterBalancer.smallerSide(2, 1, Team.RED));
        // A tie keeps the plan, which is what an idempotent replay needs.
        assertEquals(Team.RED, RosterBalancer.smallerSide(2, 2, Team.RED));
        assertEquals(Team.BLUE, RosterBalancer.smallerSide(2, 2, Team.BLUE));
    }

    @Test
    void sequentialCorrectionKeepsSidesWithinOneWhenLaunchesFail() {
        int red = 0;
        int blue = 0;
        List<RosterBalancer.Assignment> plan = RosterBalancer.balance(players(8), new Random(3L));
        int index = 0;
        for (RosterBalancer.Assignment assignment : plan) {
            boolean launchFails = index++ % 3 == 0;
            if (launchFails) {
                continue;
            }
            if (RosterBalancer.smallerSide(red, blue, assignment.team()) == Team.RED) red++;
            else blue++;
        }
        assertTrue(Math.abs(red - blue) <= 1, "successful launches must still differ by at most one");
    }

    private static List<UUID> players(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(ignored -> UUID.randomUUID()).toList();
    }
}
