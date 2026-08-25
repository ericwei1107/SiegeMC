package woo.siegePlugin.team;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamAssignmentServiceTest {

    @Test
    void assignsRedWhenResidentCountsAreTied() {
        assertEquals(Team.RED, TeamAssignmentService.selectSmallerTeam(4, 4));
    }

    @Test
    void assignsTheTeamWithFewerResidents() {
        assertEquals(Team.RED, TeamAssignmentService.selectSmallerTeam(2, 5));
        assertEquals(Team.BLUE, TeamAssignmentService.selectSmallerTeam(6, 3));
    }
}
