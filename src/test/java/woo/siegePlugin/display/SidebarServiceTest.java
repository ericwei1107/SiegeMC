package woo.siegePlugin.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SidebarServiceTest {

    @Test
    void buildsContinuousRoundEntriesWithoutAnActivityTimer() {
        YamlConfiguration config = sidebarConfig();
        SidebarSnapshot snapshot = SidebarSnapshot.initial()
                .withRound("Siege of Kazan", 10_000L)
                .withScores(120, 90)
                .withBannerControl(Team.RED, 3)
                .withSessionPoints(40, 20);

        assertEquals(List.of(
                "Map: Siege of Kazan",
                "ATK: Red Team",
                "DEF: Blue Team",
                "Red points: 120",
                "Blue points: 90",
                "Target: 10000",
                "Banner Control: Red Team (3)",
                "ATK Banner Points: 40",
                "DEF Banner Points: 20"
        ), plainLines(snapshot, config));
    }

    @Test
    void rebindingToANewRoundReplacesTheMapAndTargetWithoutTouchingScores() {
        YamlConfiguration config = sidebarConfig();
        SidebarSnapshot rebound = SidebarSnapshot.initial()
                .withRound("Siege of Kazan", 10_000L)
                .withScores(120, 90)
                .withRound("Siege of Murmansk", 5_000L);

        List<String> lines = plainLines(rebound, config);
        assertEquals("Map: Siege of Murmansk", lines.getFirst());
        assertEquals("Target: 5000", lines.get(5));
        assertEquals("Red points: 120", lines.get(3));
    }

    @Test
    void initialStateShowsTruthfulPlaceholdersBeforeTheFirstMapIsPublished() {
        YamlConfiguration config = sidebarConfig();
        List<String> lines = plainLines(SidebarSnapshot.initial(), config);

        assertEquals("Map: Preparing…", lines.getFirst());
        assertEquals("Target: —", lines.get(5));
        assertEquals("Banner Control: None (0)", lines.get(6));
        assertEquals(9, lines.size());
    }

    @Test
    void usesGoldForNeutralSidebarTextAndABoldGoldTitle() {
        YamlConfiguration config = sidebarConfig();
        List<Component> lines = SidebarService.buildLines(
                SidebarSnapshot.initial().withBannerControl(Team.RED, 3),
                SidebarSettings.fromConfig(config),
                TeamIdentityColors.fromConfig(config)
        );

        Component title = SidebarService.title(SidebarSettings.fromConfig(config));
        assertEquals(NamedTextColor.GOLD, title.color());
        assertEquals(TextDecoration.State.TRUE, title.decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.GOLD, lines.getFirst().color());
        assertEquals(NamedTextColor.GOLD, lines.get(6).color());
        assertEquals(NamedTextColor.GOLD, lines.getLast().color());
    }

    private static List<String> plainLines(SidebarSnapshot snapshot, YamlConfiguration config) {
        return SidebarService.buildLines(
                        snapshot,
                        SidebarSettings.fromConfig(config),
                        TeamIdentityColors.fromConfig(config)
                ).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
    }

    private static YamlConfiguration sidebarConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("teams.red.display-name", "Red Team");
        config.set("teams.red.color", "RED");
        config.set("teams.blue.display-name", "Blue Team");
        config.set("teams.blue.color", "BLUE");
        config.set("sidebar.title", "Siege Status");
        return config;
    }
}
