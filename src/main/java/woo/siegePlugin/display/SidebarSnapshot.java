package woo.siegePlugin.display;

import woo.siegePlugin.team.Team;

import java.time.Duration;
import java.util.Optional;

public record SidebarSnapshot(
        long redScore,
        long blueScore,
        Optional<Team> controllingTeam,
        int controllerCount,
        long redSessionPoints,
        long blueSessionPoints,
        Optional<Duration> cycleTimeRemaining
) {

    public SidebarSnapshot {
        controllingTeam = controllingTeam == null ? Optional.empty() : controllingTeam;
        cycleTimeRemaining = cycleTimeRemaining == null ? Optional.empty() : cycleTimeRemaining;
    }

    public static SidebarSnapshot initial() {
        return new SidebarSnapshot(0, 0, Optional.empty(), 0, 0, 0, Optional.empty());
    }

    public SidebarSnapshot withScores(long redScore, long blueScore) {
        return new SidebarSnapshot(
                redScore,
                blueScore,
                controllingTeam,
                controllerCount,
                redSessionPoints,
                blueSessionPoints,
                cycleTimeRemaining
        );
    }

    public SidebarSnapshot withBannerControl(Team team, int controllerCount) {
        int normalizedCount = team == null ? 0 : controllerCount;
        return new SidebarSnapshot(
                redScore,
                blueScore,
                Optional.ofNullable(team),
                normalizedCount,
                redSessionPoints,
                blueSessionPoints,
                cycleTimeRemaining
        );
    }

    public SidebarSnapshot withSessionPoints(long redSessionPoints, long blueSessionPoints) {
        return new SidebarSnapshot(
                redScore,
                blueScore,
                controllingTeam,
                controllerCount,
                redSessionPoints,
                blueSessionPoints,
                cycleTimeRemaining
        );
    }

    public SidebarSnapshot withCycleTimeRemaining(Duration remaining) {
        return new SidebarSnapshot(
                redScore,
                blueScore,
                controllingTeam,
                controllerCount,
                redSessionPoints,
                blueSessionPoints,
                Optional.ofNullable(remaining)
        );
    }
}
