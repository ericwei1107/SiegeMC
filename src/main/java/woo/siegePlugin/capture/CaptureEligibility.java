package woo.siegePlugin.capture;

/**
 * Mirrors SiegeWar's {@code doesPlayerMeetBasicSessionRequirements}, reduced to
 * the conditions this arena actually has. Losing any of these discards a
 * session's progress instead of pausing it.
 */
public final class CaptureEligibility {

    private CaptureEligibility() {
    }

    public static boolean isEligible(
            boolean online,
            boolean dead,
            boolean spectating,
            boolean flying,
            boolean gliding,
            boolean onTeam,
            boolean withinCaptureZone
    ) {
        return online
                && !dead
                && !spectating
                && !flying
                && !gliding
                && onTeam
                && withinCaptureZone;
    }
}
