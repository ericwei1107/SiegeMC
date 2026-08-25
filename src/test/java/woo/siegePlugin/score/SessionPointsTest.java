package woo.siegePlugin.score;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionPointsTest {

    private final SessionPoints points = new SessionPoints();

    @Test
    void startsEachTeamAtZero() {
        assertEquals(0L, points.get(Team.RED));
        assertEquals(0L, points.get(Team.BLUE));
    }

    @Test
    void accumulatesPerTeamIndependently() {
        points.add(Team.RED, 10L);
        points.add(Team.RED, 20L);
        points.add(Team.BLUE, 150L);

        assertEquals(30L, points.get(Team.RED));
        assertEquals(150L, points.get(Team.BLUE));
    }

    @Test
    void resetClearsBothTeams() {
        points.add(Team.RED, 30L);
        points.add(Team.BLUE, 150L);

        points.reset();

        assertEquals(0L, points.get(Team.RED));
        assertEquals(0L, points.get(Team.BLUE));
    }
}
