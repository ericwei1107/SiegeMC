package woo.siegePlugin.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMapOverridesTest {

    @TempDir
    Path directory;

    @Test
    void runtimeCapturePointSurvivesBaseManifestReplacement() throws Exception {
        Path base = directory.resolve("maps.yml");
        Files.writeString(base, completeMap(), StandardCharsets.UTF_8);
        RuntimeMapOverrides overrides = new RuntimeMapOverrides(directory.toFile());

        overrides.saveCaptureCoordinates("kazan", 101.5D, 72.0D, -30.5D);
        Files.writeString(base, completeMap().replace("x: 0.5", "x: 2.5"), StandardCharsets.UTF_8);

        SiegeMap map = overrides.loadManifest(base.toFile()).find("kazan").orElseThrow();

        assertEquals(101.5D, map.capturePoint().x());
        assertEquals(72.0D, map.capturePoint().y());
        assertEquals(-30.5D, map.capturePoint().z());
        assertTrue(directory.resolve(RuntimeMapOverrides.FILE_NAME).toFile().isFile());
    }

    @Test
    void unknownMapOverrideFailsClearly() throws Exception {
        Path base = directory.resolve("maps.yml");
        Files.writeString(base, completeMap(), StandardCharsets.UTF_8);
        RuntimeMapOverrides overrides = new RuntimeMapOverrides(directory.toFile());
        overrides.saveCaptureCoordinates("unknown", 1.0D, 64.0D, 1.0D);

        assertTrue(overrides.findConfigurationProblems(base.toFile()).getFirst().contains("unknown map 'unknown'"));
    }

    private static String completeMap() {
        return """
                maps:
                  kazan:
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
                """;
    }
}
