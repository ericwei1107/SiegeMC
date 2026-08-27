package woo.siegePlugin.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import woo.siegePlugin.arena.ArenaCleanupSettings;
import woo.siegePlugin.capture.CaptureSettings;
import woo.siegePlugin.cycle.ActivityCycleSettings;
import woo.siegePlugin.economy.CurrencySettings;
import woo.siegePlugin.economy.ShopBundle;
import woo.siegePlugin.minecart.MinecartSettings;
import woo.siegePlugin.minecart.MinecartDamageSettings;
import woo.siegePlugin.kit.KitSnapshot;
import woo.siegePlugin.kit.KitCommandSettings;
import woo.siegePlugin.score.ScoringSettings;

import java.io.File;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalConfigTest {

    @Test
    void packagedConfigUsesTheApprovedSchemaAndDefaults() {
        YamlConfiguration config = load("src/main/resources/config.yml");

        assertEquals("teamRed", config.getString("teams.red.town"));
        assertEquals("teamBlue_", config.getString("teams.blue.town"));
        assertEquals("SpectatorTown", config.getString("spectator.town"));
        assertEquals("lobby", config.getString("lobby.world"));
        assertEquals(16.0D, config.getDouble("lobby.spawn.x"));
        assertEquals(67.0D, config.getDouble("lobby.spawn.y"));
        assertEquals(-48.0D, config.getDouble("lobby.spawn.z"));

        CaptureSettings capture = CaptureSettings.fromConfig(config);
        assertEquals(Duration.ofSeconds(420), capture.sessionDuration());

        ScoringSettings scoring = ScoringSettings.fromConfig(config);
        assertEquals(Duration.ofSeconds(20), scoring.tickInterval());
        assertEquals(10L, scoring.pointsPerControllerPerTick());
        assertEquals(150L, scoring.killRewardPoints());

        ActivityCycleSettings cycle = ActivityCycleSettings.fromConfig(config);
        assertTrue(cycle.enabled());
        assertEquals(Duration.ofMinutes(45), cycle.activeDuration());
        assertEquals(Duration.ofMinutes(2), cycle.breakDuration());

        assertEquals(
                Duration.ofHours(6),
                ArenaCleanupSettings.fromConfig(config).mapResetInterval()
        );

        MinecartSettings minecarts = MinecartSettings.fromConfig(config);
        assertEquals(Duration.ofSeconds(30), minecarts.tntPlacementCooldown());
        assertEquals(Duration.ofSeconds(300), minecarts.stationaryCleanupThreshold());

        MinecartDamageSettings minecartDamage = MinecartDamageSettings.fromConfig(config);
        assertEquals(0.825D, minecartDamage.balancedCoefficient());
        assertEquals(7, minecartDamage.fullDamageDeficit());

        CurrencySettings currency = CurrencySettings.fromConfig(config);
        assertEquals(0L, currency.perKill());
        assertEquals(0L, currency.perCaptureTick());
        for (ShopBundle bundle : ShopBundle.values()) {
            assertEquals(0L, currency.priceOf(bundle), bundle + " price is not an untuned placeholder");
        }

        KitSnapshot kit = KitSnapshot.fromConfig(config);
        assertEquals(Duration.ofMinutes(5), KitCommandSettings.fromConfig(config).cooldown());
        assertEquals(17, kit.slots().size());
        assertEquals("NETHERITE_SWORD", kit.slots().get(0).material());
        assertEquals(32, kit.slots().get(3).amount());
        assertEquals("STRONG_HEALING", kit.slots().get(4).potionType());
        assertEquals("NETHERITE_BOOTS", kit.slots().get(36).material());
        assertEquals("SHIELD", kit.slots().get(40).material());
        assertTrue(KitSnapshot.findConfigurationProblems(config).isEmpty());

        assertTrue(CanonicalConfig.findConfigurationProblems(config).isEmpty());
    }

    @Test
    void everyRetiredKeyProducesAMigrationError() {
        YamlConfiguration config = new YamlConfiguration();
        for (String path : List.of(
                "scoring.banner-control-base-points",
                "scoring.enemy-death-bonus-points",
                "currency.banner-capture-reward",
                "currency.kill-reward",
                "shop.prices.cobblestone",
                "shop.prices.bow",
                "minecart.tnt-placement-cooldown-seconds",
                "minecart.sweep-interval-seconds"
        )) {
            config.set(path, 1);
        }

        List<String> problems = CanonicalConfig.findConfigurationProblems(config);

        assertEquals(8, problems.size());
        assertTrue(problems.stream().allMatch(problem -> problem.contains("is retired; use ")));
    }

    @Test
    void combatLogIsAHardDependency() {
        YamlConfiguration pluginDescription = load("src/main/resources/plugin.yml");

        assertEquals(
                List.of("Towny", "CombatLog", "Multiverse-Core"),
                pluginDescription.getStringList("depend")
        );
        assertTrue(pluginDescription.getStringList("softdepend").isEmpty());
        assertTrue(pluginDescription.isConfigurationSection("permissions.siege.admin.resetscores"));
        assertTrue(pluginDescription.isConfigurationSection("permissions.siege.minecart.cooldown.bypass"));
        assertTrue(pluginDescription.isConfigurationSection("permissions.siege.spectate"));
        assertTrue(pluginDescription.isConfigurationSection("permissions.siege.rejoin"));
        assertTrue(pluginDescription.isConfigurationSection("permissions.siege.join"));
        assertTrue(pluginDescription.isConfigurationSection("permissions.siege.lobby"));
        assertTrue(pluginDescription.getString("commands.siege.usage").contains("join|lobby"));
    }

    private static YamlConfiguration load(String path) {
        return YamlConfiguration.loadConfiguration(new File(path));
    }
}
