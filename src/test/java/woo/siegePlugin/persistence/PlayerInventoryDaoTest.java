package woo.siegePlugin.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerInventoryDaoTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingPlayerHasNoStoredInventory() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            Optional<byte[]> stored = await(new PlayerInventoryDao(database).load(UUID.randomUUID()));

            assertEquals(Optional.empty(), stored);
        }
    }

    @Test
    void saveCreatesAndThenReplacesStoredInventory() throws Exception {
        UUID playerId = UUID.randomUUID();
        try (SiegeDatabase database = openDatabase()) {
            PlayerInventoryDao dao = new PlayerInventoryDao(database);

            await(dao.save(playerId, new byte[]{1, 2, 3}));
            assertArrayEquals(new byte[]{1, 2, 3}, await(dao.load(playerId)).orElseThrow());

            await(dao.save(playerId, new byte[]{9, 8}));
            assertArrayEquals(new byte[]{9, 8}, await(dao.load(playerId)).orElseThrow());
        }
    }

    @Test
    void closeFlushesQueuedWritesBeforeStoppingWorker() throws Exception {
        Path databasePath = temporaryDirectory.resolve("flush.db");
        UUID playerId = UUID.randomUUID();
        SiegeDatabase writer = new SiegeDatabase(databasePath);
        await(writer.initialized());
        CompletableFuture<Void> queuedWrite = new PlayerInventoryDao(writer).save(playerId, new byte[]{4, 4, 4});

        writer.close();
        await(queuedWrite);

        try (SiegeDatabase reader = new SiegeDatabase(databasePath)) {
            await(reader.initialized());
            assertArrayEquals(
                    new byte[]{4, 4, 4},
                    await(new PlayerInventoryDao(reader).load(playerId)).orElseThrow()
            );
        }
    }

    @Test
    void newOperationsAreRejectedAfterClose() throws Exception {
        SiegeDatabase database = openDatabase();
        PlayerInventoryDao dao = new PlayerInventoryDao(database);
        database.close();

        CompletableFuture<Optional<byte[]>> rejected = dao.load(UUID.randomUUID());

        assertThrows(ExecutionException.class, () -> await(rejected));
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
