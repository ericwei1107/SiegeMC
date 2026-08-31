package woo.siegePlugin.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanCopyDirectoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void copyExcludesWorldLocksAndOnlyDeletesGeneratedActiveCopies() throws IOException {
        Path template = Files.createDirectories(temporaryDirectory.resolve("template"));
        Files.writeString(template.resolve("level.dat"), "world");
        Files.writeString(template.resolve("session.lock"), "lock");
        Files.writeString(template.resolve("uid.dat"), "uid");
        Path nested = Files.createDirectories(template.resolve("region"));
        Files.writeString(nested.resolve("r.0.0.mca"), "region");
        Path activeRoot = temporaryDirectory.resolve("active");

        Path activeCopy = CleanCopyDirectory.copyTemplate(template, activeRoot, "siege-active-12-al_quds");

        assertTrue(Files.exists(activeCopy.resolve("level.dat")));
        assertTrue(Files.exists(activeCopy.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(activeCopy.resolve("session.lock")));
        assertFalse(Files.exists(activeCopy.resolve("uid.dat")));

        CleanCopyDirectory.deleteActiveCopy(activeRoot, activeCopy);
        assertFalse(Files.exists(activeCopy));
        assertTrue(Files.exists(template));
    }

    @Test
    void refusesToDeleteNonGeneratedOrOutOfRootDirectories() throws IOException {
        Path activeRoot = Files.createDirectories(temporaryDirectory.resolve("active"));
        Path template = Files.createDirectories(temporaryDirectory.resolve("template"));

        assertThrows(IllegalArgumentException.class, () -> CleanCopyDirectory.deleteActiveCopy(activeRoot, template));
        assertThrows(IllegalArgumentException.class, () -> CleanCopyDirectory.copyTemplate(template, activeRoot, "map-copy"));
    }

    @Test
    void promotionPreservesCalibrationEditsAndBacksUpPreviousTemplate() throws IOException {
        Path templateRoot = Files.createDirectories(temporaryDirectory.resolve("templates"));
        Path template = Files.createDirectories(templateRoot.resolve("iron_mountain1"));
        Files.writeString(template.resolve("level.dat"), "old-level");
        Files.writeString(template.resolve("old-blocks.dat"), "old");

        Path activeRoot = Files.createDirectories(temporaryDirectory.resolve("worlds"));
        Path calibration = Files.createDirectories(activeRoot.resolve("siege-active-12-iron_mountain1"));
        Files.writeString(calibration.resolve("level.dat"), "new-level");
        Files.writeString(calibration.resolve("placed-chests.dat"), "double chests");
        Files.writeString(calibration.resolve("session.lock"), "runtime lock");
        Files.writeString(calibration.resolve("uid.dat"), "runtime uid");

        Path backup = CleanCopyDirectory.promoteActiveCopy(
                activeRoot, calibration, templateRoot, "iron_mountain1"
        );

        assertEquals("new-level", Files.readString(template.resolve("level.dat")));
        assertTrue(Files.exists(template.resolve("placed-chests.dat")));
        assertFalse(Files.exists(template.resolve("session.lock")));
        assertFalse(Files.exists(template.resolve("uid.dat")));
        assertFalse(Files.exists(calibration));
        assertTrue(backup != null && Files.exists(backup.resolve("old-blocks.dat")));
    }
}
