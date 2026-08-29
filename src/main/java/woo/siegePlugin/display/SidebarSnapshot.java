package woo.siegePlugin.display;

import woo.siegePlugin.team.Team;

import java.util.Optional;

public record SidebarSnapshot(
        String mapDisplayName,
        long scoreLimit,
        long redScore,
        long blueScore,
        Optional<Team> controllingTeam,
        int controllerCount,
        long redSessionPoints,
        long blueSessionPoints
) {

    public SidebarSnapshot {
        controllingTeam = controllingTeam == null ? Optional.empty() : controllingTeam;
        mapDisplayName = mapDisplayName == null ? "" : mapDisplayName;
    }

    public static SidebarSnapshot initial() {
        return new SidebarSnapshot("", 0, 0, 0, Optional.empty(), 0, 0, 0);
    }

    /** Rebinds the sidebar to a newly published round. */
    public SidebarSnapshot withRound(String mapDisplayName, long scoreLimit) {
        return new SidebarSnapshot(
                mapDisplayName,
                scoreLimit,
                redScore,
                blueScore,
                controllingTeam,
                controllerCount,
                redSessionPoints,
                blueSessionPoints
        );
    }

    public SidebarSnapshot withScores(long redScore, long blueScore) {
        return new SidebarSnapshot(
                mapDisplayName,
                scoreLimit,
                redScore,
                blueScore,
                controllingTeam,
                controllerCount,
                redSessionPoints,
                blueSessionPoints
        );
    }

    public SidebarSnapshot withBannerControl(Team team, int controllerCount) {
        int normalizedCount = team == null ? 0 : Math.max(0, controllerCount);
        return new SidebarSnapshot(
                mapDisplayName,
                scoreLimit,
                redScore,
                blueScore,
                Optional.ofNullable(team),
                normalizedCount,
                redSessionPoints,
                blueSessionPoints
        );
    }

    public SidebarSnapshot withSessionPoints(long redSessionPoints, long blueSessionPoints) {
        return new SidebarSnapshot(
                mapDisplayName,
                scoreLimit,
                redScore,
                blueScore,
                controllingTeam,
                controllerCount,
                redSessionPoints,
                blueSessionPoints
        );
    }
}
