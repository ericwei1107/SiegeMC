package woo.siegePlugin.display;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TownyAdapter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns the personal scoreboard assigned to each online player. Stage 4.4d.2
 * will add its sidebar objective to these same scoreboard instances.
 */
public final class TeamDisplayService {

    private static final String FRIENDLY_TEAM = "friendly";
    private static final String ENEMY_TEAM = "enemy";
    private static final String DISPLAY_RED_TEAM = "display-red";
    private static final String DISPLAY_BLUE_TEAM = "display-blue";

    private final Server server;
    private final ScoreboardManager scoreboardManager;
    private final TownyAdapter townyAdapter;
    private final TeamIdentityColors identityColors;
    private final Map<UUID, Scoreboard> personalScoreboards = new java.util.HashMap<>();

    public TeamDisplayService(
            Server server,
            TownyAdapter townyAdapter,
            TeamIdentityColors identityColors
    ) {
        this.server = server;
        this.scoreboardManager = server.getScoreboardManager();
        this.townyAdapter = townyAdapter;
        this.identityColors = identityColors;
    }

    public void initializeOnlinePlayers() {
        for (Player player : server.getOnlinePlayers()) {
            rebuildViewer(player);
        }
    }

    public void handleJoin(Player player) {
        if (!player.isOnline()) {
            return;
        }
        rebuildViewer(player);
        updatePlayerForOtherViewers(player);
    }

    public void handleQuit(Player player) {
        personalScoreboards.remove(player.getUniqueId());
        for (Player viewer : server.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                Scoreboard scoreboard = personalScoreboards.get(viewer.getUniqueId());
                if (scoreboard != null) {
                    removeEntry(scoreboard, player.getName());
                }
            }
        }
    }

    /**
     * Rebuilds the switcher's entire relative view, then moves just their
     * entry on every other viewer's board.
     */
    public void handleTeamSwitch(Player player) {
        rebuildViewer(player);
        updatePlayerForOtherViewers(player);
    }

    public Scoreboard getOrCreateScoreboard(Player viewer) {
        return personalScoreboards.computeIfAbsent(
                viewer.getUniqueId(),
                ignored -> scoreboardManager.getNewScoreboard()
        );
    }

    private void rebuildViewer(Player viewer) {
        Scoreboard scoreboard = getOrCreateScoreboard(viewer);
        for (org.bukkit.scoreboard.Team existingTeam : scoreboard.getTeams()) {
            existingTeam.unregister();
        }

        Team viewerTeam = townyAdapter.getPlayerTeam(viewer).orElse(null);
        Map<DisplayGroup, org.bukkit.scoreboard.Team> displayTeams = viewerTeam == null
                ? createAbsoluteTeams(scoreboard)
                : createRelativeTeams(scoreboard);

        for (Player subject : server.getOnlinePlayers()) {
            Team subjectTeam = townyAdapter.getPlayerTeam(subject).orElse(null);
            DisplayGroup group = classify(viewerTeam, subjectTeam);
            org.bukkit.scoreboard.Team displayTeam = displayTeams.get(group);
            if (displayTeam != null) {
                displayTeam.addEntry(subject.getName());
            }
        }

        viewer.setScoreboard(scoreboard);
    }

    private Map<DisplayGroup, org.bukkit.scoreboard.Team> createRelativeTeams(Scoreboard scoreboard) {
        Map<DisplayGroup, org.bukkit.scoreboard.Team> teams = new EnumMap<>(DisplayGroup.class);

        org.bukkit.scoreboard.Team friendly = scoreboard.registerNewTeam(FRIENDLY_TEAM);
        friendly.color(NamedTextColor.GREEN);
        friendly.setAllowFriendlyFire(false);
        friendly.setOption(
                org.bukkit.scoreboard.Team.Option.COLLISION_RULE,
                org.bukkit.scoreboard.Team.OptionStatus.NEVER
        );
        teams.put(DisplayGroup.FRIENDLY, friendly);

        org.bukkit.scoreboard.Team enemy = scoreboard.registerNewTeam(ENEMY_TEAM);
        enemy.color(NamedTextColor.RED);
        teams.put(DisplayGroup.ENEMY, enemy);
        return teams;
    }

    private Map<DisplayGroup, org.bukkit.scoreboard.Team> createAbsoluteTeams(Scoreboard scoreboard) {
        Map<DisplayGroup, org.bukkit.scoreboard.Team> teams = new EnumMap<>(DisplayGroup.class);

        org.bukkit.scoreboard.Team red = scoreboard.registerNewTeam(DISPLAY_RED_TEAM);
        red.color(identityColors.get(Team.RED));
        teams.put(DisplayGroup.DISPLAY_RED, red);

        org.bukkit.scoreboard.Team blue = scoreboard.registerNewTeam(DISPLAY_BLUE_TEAM);
        blue.color(identityColors.get(Team.BLUE));
        teams.put(DisplayGroup.DISPLAY_BLUE, blue);
        return teams;
    }

    private void updatePlayerForOtherViewers(Player changedPlayer) {
        Team changedTeam = townyAdapter.getPlayerTeam(changedPlayer).orElse(null);

        for (Player viewer : server.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(changedPlayer.getUniqueId())) {
                continue;
            }

            Scoreboard scoreboard = personalScoreboards.get(viewer.getUniqueId());
            if (scoreboard == null) {
                rebuildViewer(viewer);
                scoreboard = personalScoreboards.get(viewer.getUniqueId());
            }
            removeEntry(scoreboard, changedPlayer.getName());

            Team viewerTeam = townyAdapter.getPlayerTeam(viewer).orElse(null);
            DisplayGroup group = classify(viewerTeam, changedTeam);
            org.bukkit.scoreboard.Team target = scoreboard.getTeam(teamName(group));
            if (target != null) {
                target.addEntry(changedPlayer.getName());
            }
        }
    }

    private static void removeEntry(Scoreboard scoreboard, String entry) {
        for (org.bukkit.scoreboard.Team team : scoreboard.getTeams()) {
            team.removeEntry(entry);
        }
    }

    static DisplayGroup classify(Team viewerTeam, Team subjectTeam) {
        if (subjectTeam == null) {
            return DisplayGroup.NONE;
        }
        if (viewerTeam == null) {
            return subjectTeam == Team.RED ? DisplayGroup.DISPLAY_RED : DisplayGroup.DISPLAY_BLUE;
        }
        return viewerTeam == subjectTeam ? DisplayGroup.FRIENDLY : DisplayGroup.ENEMY;
    }

    private static String teamName(DisplayGroup group) {
        return switch (Objects.requireNonNull(group)) {
            case FRIENDLY -> FRIENDLY_TEAM;
            case ENEMY -> ENEMY_TEAM;
            case DISPLAY_RED -> DISPLAY_RED_TEAM;
            case DISPLAY_BLUE -> DISPLAY_BLUE_TEAM;
            case NONE -> "";
        };
    }

    enum DisplayGroup {
        FRIENDLY,
        ENEMY,
        DISPLAY_RED,
        DISPLAY_BLUE,
        NONE
    }
}
