package woo.siegePlugin.capture;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureControlTest {

    private final CaptureControl control = new CaptureControl();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    @Test
    void startsUncontrolled() {
        assertEquals(Optional.empty(), control.controllingTeam());
        assertEquals(0, control.controllerCount());
    }

    @Test
    void firstCompletionGainsControl() {
        assertEquals(CaptureControl.Outcome.CONTROL_GAINED, control.completeSession(first, Team.RED));
        assertEquals(Optional.of(Team.RED), control.controllingTeam());
        assertEquals(1, control.controllerCount());
    }

    @Test
    void sameSideCompletionAddsAnotherController() {
        control.completeSession(first, Team.RED);

        assertEquals(CaptureControl.Outcome.CONTROLLER_ADDED, control.completeSession(second, Team.RED));
        assertEquals(Optional.of(Team.RED), control.controllingTeam());
        assertEquals(2, control.controllerCount());
    }

    @Test
    void repeatedCompletionByTheSameControllerDoesNotDoubleCount() {
        control.completeSession(first, Team.RED);
        control.completeSession(first, Team.RED);

        assertEquals(1, control.controllerCount());
    }

    @Test
    void opposingCompletionClearsPreviousControllersAndTransfersControl() {
        control.completeSession(first, Team.RED);
        control.completeSession(second, Team.RED);

        assertEquals(CaptureControl.Outcome.CONTROL_REVERSED, control.completeSession(third, Team.BLUE));
        assertEquals(Optional.of(Team.BLUE), control.controllingTeam());
        assertEquals(1, control.controllerCount());
        assertFalse(control.isController(first));
        assertFalse(control.isController(second));
        assertTrue(control.isController(third));
    }

    @Test
    void removingTheLastControllerKeepsTheControllingSide() {
        control.completeSession(first, Team.RED);

        assertTrue(control.removeController(first));
        assertEquals(Optional.of(Team.RED), control.controllingTeam());
        assertEquals(0, control.controllerCount());
    }

    @Test
    void aRemovedControllerCanEarnControlCreditAgain() {
        control.completeSession(first, Team.RED);
        control.removeController(first);

        assertEquals(CaptureControl.Outcome.CONTROLLER_ADDED, control.completeSession(first, Team.RED));
        assertEquals(1, control.controllerCount());
    }

    @Test
    void removingAPlayerWhoNeverControlledReportsNoChange() {
        assertFalse(control.removeController(first));
    }

    @Test
    void resetSurrendersTheBanner() {
        control.completeSession(first, Team.RED);
        control.completeSession(second, Team.RED);

        control.reset();

        assertEquals(Optional.empty(), control.controllingTeam());
        assertEquals(0, control.controllerCount());
        assertEquals(CaptureControl.Outcome.CONTROL_GAINED, control.completeSession(third, Team.BLUE));
    }
}
