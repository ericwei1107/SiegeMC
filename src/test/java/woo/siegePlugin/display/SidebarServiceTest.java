package woo.siegePlugin.display;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SidebarServiceTest {

    @Test
    void buildsTheEightEntriesBelowTheObjectiveTitle() {
        YamlConfiguration config = sidebarConfig();
        SidebarSnapshot snapshot = SidebarSnapshot.initial()
                .withScores(120, 90)
                .withBannerControl(Team.RED, 3)
                .withSessionPoints(40, 20)
                .withCycleTimeRemaining(Duration.ofMinutes(12).plusSeconds(5));

        List<String> lines = SidebarService.buildLines(
                        snapshot,
                        SidebarSettings.fromConfig(config),
                        TeamIdentityColors.fromConfig(config)
                ).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();

        assertEquals(List.of(
                "ATK: Red Team",
                "DEF: Blue Team",
                "Red points: 120",
                "Blue points: 90",
                "Banner Control: Red Team (3)",
                "ATK BAT Points: 40",
                "DEF BAT Points: 20",
                "BAT Time Left: 12:05"
        ), lines);
    }

    @Test
    void initialStateShowsTruthfulPlaceholdersForFutureSystems() {
        YamlConfiguration config = sidebarConfig();
        List<String> lines = SidebarService.buildLines(
                        SidebarSnapshot.initial(),
                        SidebarSettings.fromConfig(config),
                        TeamIdentityColors.fromConfig(config)
                ).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();

        assertEquals("Banner Control: None (0)", lines.get(4));
        assertEquals("BAT Time Left: Not started", lines.get(7));
    }

    @Test
    void formatsLongCycleDurations() {
        assertEquals("45:00", SidebarService.formatDuration(Duration.ofMinutes(45)));
        assertEquals("1:02:03", SidebarService.formatDuration(Duration.ofHours(1).plusMinutes(2).plusSeconds(3)));
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
