package woo.siegePlugin.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
