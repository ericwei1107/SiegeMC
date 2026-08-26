package woo.siegePlugin.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerStateTransitionServiceTest {

    @Test
    void lobbyEntryDeniesEscapeDuringCombatOrCapture() {
        assertEquals(
                PlayerStateTransitionService.TransitionResult.COMBAT_TAGGED,
                PlayerStateTransitionService.checkLobbyEntry(false, false, false, true, false)
        );
        assertEquals(
                PlayerStateTransitionService.TransitionResult.CAPTURE_SESSION_ACTIVE,
                PlayerStateTransitionService.checkLobbyEntry(false, false, false, false, true)
        );
        assertEquals(
                PlayerStateTransitionService.TransitionResult.STARTED,
                PlayerStateTransitionService.checkLobbyEntry(false, false, false, false, false)
        );
    }

    @Test
    void lobbyWorldAndSpectatorTownRemainSeparateContexts() {
        assertEquals(
                PlayerStateTransitionService.TransitionResult.ALREADY_IN_LOBBY,
                PlayerStateTransitionService.checkLobbyEntry(false, false, true, false, false)
        );
        assertEquals(
                PlayerStateTransitionService.TransitionResult.SPECTATOR_CONTEXT,
                PlayerStateTransitionService.checkSiegeEntry(false, true, false, true)
        );
        assertEquals(
                PlayerStateTransitionService.TransitionResult.SPECTATOR_CONTEXT,
                PlayerStateTransitionService.checkSpectatorEntry(false, true, false, false)
        );
    }

    @Test
    void siegeEntryOnlyStartsFromLobbyAndBlocksConcurrentRequests() {
        assertEquals(
                PlayerStateTransitionService.TransitionResult.STARTED,
                PlayerStateTransitionService.checkSiegeEntry(false, false, false, true)
        );
        assertEquals(
                PlayerStateTransitionService.TransitionResult.ALREADY_IN_SIEGE,
                PlayerStateTransitionService.checkSiegeEntry(false, false, true, false)
        );
        assertEquals(
                PlayerStateTransitionService.TransitionResult.TRANSITION_IN_PROGRESS,
                PlayerStateTransitionService.checkSiegeEntry(true, false, false, true)
        );
    }
}
