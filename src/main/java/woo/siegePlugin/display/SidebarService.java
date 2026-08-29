package woo.siegePlugin.display;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import woo.siegePlugin.team.Team;

import java.util.List;

/**
 * Renders shared siege state onto every player's personal scoreboard. Later
 * scoring and capture services call the update methods here.
 */
public final class SidebarService {

    private static final String OBJECTIVE_NAME = "siege_status";

    private final Server server;
    private final TeamDisplayService teamDisplayService;
    private final SidebarSettings settings;
    private final TeamIdentityColors identityColors;
    private SidebarSnapshot snapshot = SidebarSnapshot.initial();

    public SidebarService(
            Server server,
            TeamDisplayService teamDisplayService,
            SidebarSettings settings,
            TeamIdentityColors identityColors
    ) {
        this.server = server;
        this.teamDisplayService = teamDisplayService;
        this.settings = settings;
        this.identityColors = identityColors;
    }

    public void initializePlayer(Player player) {
        initializePlayer(player, buildLines(snapshot, settings, identityColors));
    }

    private void initializePlayer(Player player, List<Component> lines) {
        Scoreboard scoreboard = teamDisplayService.getOrCreateScoreboard(player);
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                    OBJECTIVE_NAME,
                    Criteria.DUMMY,
                    title(settings),
                    RenderType.INTEGER
            );
            objective.numberFormat(NumberFormat.blank());
        } else {
            objective.displayName(title(settings));
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        render(objective, lines);
    }

    /** Called once per activation so every viewer sees the live map and target. */
    public void updateRound(String mapDisplayName, long scoreLimit) {
        updateSnapshot(snapshot.withRound(mapDisplayName, scoreLimit));
    }

    public void updateScores(long redScore, long blueScore) {
        updateSnapshot(snapshot.withScores(redScore, blueScore));
    }

    public void updateBannerControl(Team controllingTeam, int controllerCount) {
        updateSnapshot(snapshot.withBannerControl(controllingTeam, controllerCount));
    }

    public void updateSessionPoints(long redSessionPoints, long blueSessionPoints) {
        updateSnapshot(snapshot.withSessionPoints(redSessionPoints, blueSessionPoints));
    }

    public SidebarSnapshot snapshot() {
        return snapshot;
    }

    private void updateSnapshot(SidebarSnapshot updated) {
        if (snapshot.equals(updated)) {
            return;
        }
        snapshot = updated;
        List<Component> lines = buildLines(snapshot, settings, identityColors);
        for (Player player : server.getOnlinePlayers()) {
            initializePlayer(player, lines);
        }
    }

    private void render(Objective objective, List<Component> lines) {
        int scoreValue = lines.size();
        for (int index = 0; index < lines.size(); index++) {
            Score score = objective.getScore("siege-line-" + index);
            score.customName(lines.get(index));
            score.setScore(scoreValue - index);
        }
    }

    static List<Component> buildLines(
            SidebarSnapshot state,
            SidebarSettings settings,
            TeamIdentityColors identityColors
    ) {
        Component redName = Component.text(settings.displayName(Team.RED), identityColors.get(Team.RED));
        Component blueName = Component.text(settings.displayName(Team.BLUE), identityColors.get(Team.BLUE));

        Component bannerControl = state.controllingTeam()
                .<Component>map(team -> Component.text(settings.displayName(team), identityColors.get(team))
                        .append(Component.text(" (" + state.controllerCount() + ")", NamedTextColor.GOLD)))
                .orElse(Component.text("None (0)", NamedTextColor.GRAY));

        return List.of(
                label("Map: ").append(Component.text(
                        state.mapDisplayName().isBlank() ? "Preparing…" : state.mapDisplayName(),
                        NamedTextColor.WHITE
                )),
                label("ATK: ").append(redName),
                label("DEF: ").append(blueName),
                label("Red points: ").append(Component.text(state.redScore(), identityColors.get(Team.RED))),
                label("Blue points: ").append(Component.text(state.blueScore(), identityColors.get(Team.BLUE))),
                label("Target: ").append(Component.text(
                        state.scoreLimit() <= 0L ? "—" : Long.toString(state.scoreLimit()),
                        NamedTextColor.WHITE
                )),
                label("Banner Control: ").append(bannerControl),
                label("ATK Banner Points: ").append(Component.text(state.redSessionPoints(), identityColors.get(Team.RED))),
                label("DEF Banner Points: ").append(Component.text(state.blueSessionPoints(), identityColors.get(Team.BLUE)))
        );
    }

    static Component title(SidebarSettings settings) {
        return Component.text(settings.title(), NamedTextColor.GOLD, TextDecoration.BOLD);
    }

    private static Component label(String value) {
        return Component.text(value, NamedTextColor.GOLD);
    }
}
