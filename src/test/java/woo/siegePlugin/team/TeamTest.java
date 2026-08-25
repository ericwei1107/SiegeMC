package woo.siegePlugin.team;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamTest {

    @Test
    void parsesPlayerFacingTeamNamesCaseInsensitively() {
        assertEquals(Team.RED, Team.fromInput("RED").orElseThrow());
        assertEquals(Team.BLUE, Team.fromInput("blue").orElseThrow());
        assertTrue(Team.fromInput("green").isEmpty());
    }

    @Test
    void returnsTheOpposingTeam() {
        assertEquals(Team.BLUE, Team.RED.opponent());
        assertEquals(Team.RED, Team.BLUE.opponent());
    }
}
