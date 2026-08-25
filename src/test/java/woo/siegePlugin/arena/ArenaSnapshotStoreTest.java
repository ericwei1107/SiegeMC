package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaSnapshotStoreTest {

    @TempDir
    Path temporaryDirectory;

    private ArenaSnapshotStore store() {
        return new ArenaSnapshotStore(temporaryDirectory.resolve("snapshot"));
    }

    @Test
    void reportsNoSnapshotBeforeOneIsTaken() {
        ArenaSnapshotStore store = store();

        assertFalse(store.exists());
        assertEquals(Optional.empty(), store.loadRegion());
    }

    @Test
    void tileFilesWithoutAManifestDoNotCountAsASnapshot() throws Exception {
        ArenaSnapshotStore store = store();
        store.ensureDirectoryExists();
        Files.writeString(store.tileFile(new ArenaTile(0, 0, 0, 16, 16, 16)).toPath(), "partial");

        // A crashed capture must never look like a usable snapshot.
        assertFalse(store.exists());
    }

    @Test
    void aWrittenManifestRoundTripsTheRegion() throws Exception {
        ArenaSnapshotStore store = store();
        store.ensureDirectoryExists();
        ArenaRegion region = ArenaRegion.between("siegeworld", -5, 60, 7, 26, 80, 40);

        store.writeManifest(region, region.tileCount());

        assertTrue(store.exists());
        assertEquals(Optional.of(region), store.loadRegion());
    }

    @Test
    void clearRemovesTilesAndTheManifest() throws Exception {
        ArenaSnapshotStore store = store();
        store.ensureDirectoryExists();
        ArenaTile tile = new ArenaTile(0, 0, 0, 16, 16, 16);
        Files.writeString(store.tileFile(tile).toPath(), "old");
        store.writeManifest(ArenaRegion.between("siegeworld", 0, 0, 0, 15, 15, 15), 1);

        store.clear();

        assertFalse(store.exists());
        assertFalse(Files.exists(store.tileFile(tile).toPath()));
    }

    @Test
    void clearOnAMissingDirectoryCreatesItInstead() throws Exception {
        ArenaSnapshotStore store = store();

        store.clear();

        assertTrue(Files.isDirectory(store.directory()));
    }

    @Test
    void tileFilesLandInsideTheSnapshotDirectory() {
        ArenaSnapshotStore store = store();

        Path tilePath = store.tileFile(new ArenaTile(16, 64, -32, 16, 16, 16)).toPath();

        assertEquals(store.directory(), tilePath.getParent());
        assertEquals("tile_16_64_-32.nbt", tilePath.getFileName().toString());
    }
}
