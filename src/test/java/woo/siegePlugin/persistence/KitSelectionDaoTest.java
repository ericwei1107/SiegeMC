package woo.siegePlugin.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitSelectionDaoTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @TempDir
    Path temporaryDirectory;

    @Test
    void unknownPlayerLoadsAsTheDefaultSelection() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            assertTrue(await(new KitSelectionDao(database).load(UUID.randomUUID())).isEmpty());
        }
    }

    @Test
    void saveReplacesEveryChoiceAndEmptySaveResetsToDefault() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            KitSelectionDao dao = new KitSelectionDao(database);
            UUID player = UUID.randomUUID();

            await(dao.save(player, Map.of(2, "food", 4, "healing")));
            assertEquals(Map.of(2, "food", 4, "healing"), await(dao.load(player)));

            await(dao.save(player, Map.of(8, "speed")));
            assertEquals(Map.of(8, "speed"), await(dao.load(player)));

            await(dao.save(player, Map.of()));
            assertTrue(await(dao.load(player)).isEmpty());
        }
    }

    @Test
    void serializedLegacyRowsRemainUntouched() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            UUID player = UUID.randomUUID();
            await(database.submit(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO kit_loadouts (player_uuid, loadout, updated_at)
                        VALUES (?, ?, 0)
                        """)) {
                    statement.setString(1, player.toString());
                    statement.setBytes(2, new byte[]{1, 2, 3});
                    statement.executeUpdate();
                }
                return null;
            }));

            await(new KitSelectionDao(database).save(player, Map.of(2, "food")));

            int legacyRows = await(database.submit(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM kit_loadouts WHERE player_uuid = ?"
                )) {
                    statement.setString(1, player.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        return result.getInt(1);
                    }
                }
            }));
            assertEquals(1, legacyRows);
        }
    }

    @Test
    void serializedWorkerOrderingMakesTheLastQueuedSaveWin() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            KitSelectionDao dao = new KitSelectionDao(database);
            UUID player = UUID.randomUUID();

            CompletableFuture<Void> first = dao.save(player, Map.of(2, "food"));
            CompletableFuture<Void> second = dao.save(player, Map.of(4, "healing"));
            await(first);
            await(second);

            assertEquals(Map.of(4, "healing"), await(dao.load(player)));
        }
    }

    @Test
    void selectionsSurviveDatabaseReopen() throws Exception {
        Path path = temporaryDirectory.resolve("kits.db");
        UUID player = UUID.randomUUID();
        try (SiegeDatabase database = new SiegeDatabase(path)) {
            await(database.initialized());
            await(new KitSelectionDao(database).save(player, Map.of(2, "food")));
        }

        try (SiegeDatabase reopened = new SiegeDatabase(path)) {
            await(reopened.initialized());
            assertEquals(Map.of(2, "food"), await(new KitSelectionDao(reopened).load(player)));
        }
    }

    private SiegeDatabase openDatabase() throws Exception {
        SiegeDatabase database = new SiegeDatabase(temporaryDirectory.resolve(UUID.randomUUID() + ".db"));
        await(database.initialized());
        return database;
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }
}
