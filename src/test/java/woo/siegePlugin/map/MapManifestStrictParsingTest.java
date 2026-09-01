package woo.siegePlugin.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import woo.siegePlugin.team.Team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manifest parsing must fail loudly.
 *
 * <p>Bukkit's own loader turns a YAML syntax error into an empty configuration,
 * which is indistinguishable from "no maps enabled" — and would silently empty
 * the whole rotation pool.</p>
 */
class MapManifestStrictParsingTest {

    @TempDir Path directory;

    @Test
    void malformedYamlThrowsInsteadOfProducingAnEmptyPool() throws Exception {
        Path file = write("maps:\n  kazan:\n    enabled: true\n   bad-indent: oops\n");

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> MapManifest.load(file.toFile()));
        assertTrue(failure.getMessage().contains("not valid YAML"), failure.getMessage());
        assertEquals(1, MapManifest.findConfigurationProblems(file.toFile()).size());
    }

    @Test
    void aMissingFileIsReportedRatherThanTreatedAsEmpty() {
        Path missing = directory.resolve("absent.yml");
        assertThrows(IllegalArgumentException.class, () -> MapManifest.load(missing.toFile()));
    }

    @Test
    void anIdOutsideTheSafeCharacterSetIsRejected() throws Exception {
        Path file = write(complete("bad id"));
        List<String> problems = MapManifest.findConfigurationProblems(file.toFile());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("only letters, numbers, underscores, and hyphens"),
                problems.toString());
    }

    @Test
    void everyBoundsEdgeMustBeStatedOutright() throws Exception {
        Path file = write(complete("kazan").replace("      min-z: -256\n", ""));
        List<String> problems = MapManifest.findConfigurationProblems(file.toFile());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("bounds.min-z must be a number"), problems.toString());
    }

    @Test
    void aNonFiniteCoordinateIsRejected() throws Exception {
        Path file = write(complete("kazan").replace("      x: 0.5\n      y: 70.0\n      z: 0.5\n      radius: 16",
                "      x: .inf\n      y: 70.0\n      z: 0.5\n      radius: 16"));
        List<String> problems = MapManifest.findConfigurationProblems(file.toFile());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("must be a finite number"), problems.toString());
    }

    @Test
    void aCompleteEntryLoads() throws Exception {
        MapManifest manifest = MapManifest.load(write(complete("kazan")).toFile());
        SiegeMap map = manifest.find("kazan").orElseThrow();
        assertEquals("Siege of Kazan", map.displayName());
        assertEquals(16, map.captureRadius());
        assertEquals(-256, map.bounds().minX());
    }

    @Test
    void aDisabledEntryIsIgnoredEvenWhenIncomplete() throws Exception {
        Path file = write("maps:\n  kazan:\n    enabled: false\n    display-name: \"Half done\"\n");
        assertTrue(MapManifest.findConfigurationProblems(file.toFile()).isEmpty());
        assertTrue(MapManifest.load(file.toFile()).rotationPool().isEmpty());
    }

    @Test
    void teamOwnedBaseClaimsLoadAndCrossTeamOverlapIsRejected() throws Exception {
        String claims = """
                    base-claims:
                      red:
                        - { chunk-x: -2, chunk-z: 0 }
                      blue:
                        - { chunk-x: 1, chunk-z: 0 }
                """;
        Path valid = write(complete("kazan") + claims);
        SiegeMap map = MapManifest.load(valid.toFile()).find("kazan").orElseThrow();
        assertEquals(1, map.claimsFor(Team.RED).size());
        assertEquals(1, map.claimsFor(Team.BLUE).size());

        Path overlap = write((complete("kazan") + claims)
                .replace("chunk-x: 1", "chunk-x: -2"));
        assertTrue(MapManifest.findConfigurationProblems(overlap.toFile()).getFirst()
                .contains("assigned to both teams"));
    }

    @Test
    void fractionalBaseClaimCoordinatesAreRejectedRatherThanRounded() throws Exception {
        String claims = """
                    base-claims:
                      red:
                        - { chunk-x: 1.5, chunk-z: 0 }
                """;

        List<String> problems = MapManifest.findConfigurationProblems(
                write(complete("kazan") + claims).toFile()
        );

        assertTrue(problems.getFirst().contains("must be a whole chunk coordinate"), problems.toString());
    }

    private Path write(String contents) throws Exception {
        Path file = directory.resolve("maps-" + java.util.UUID.randomUUID() + ".yml");
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    private static String complete(String id) {
        return """
                maps:
                  "%s":
                    enabled: true
                    display-name: "Siege of Kazan"
                    template-folder: "kazan"
                    red-spawn:
                      x: -10.5
                      y: 70.0
                      z: -10.5
                    blue-spawn:
                      x: 10.5
                      y: 70.0
                      z: 10.5
                    capture-point:
                      x: 0.5
                      y: 70.0
                      z: 0.5
                      radius: 16
                    bounds:
                      min-x: -256
                      min-z: -256
                      max-x: 256
                      max-z: 256
                """.formatted(id);
    }
}
