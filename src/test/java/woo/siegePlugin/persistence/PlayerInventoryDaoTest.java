package woo.siegePlugin.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerInventoryDaoTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingPlayerHasNoStoredInventory() throws Exception {
        try (PlayerInventoryDao dao = openDao()) {
            Optional<byte[]> stored = dao.load(UUID.randomUUID()).get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

            assertEquals(Optional.empty(), stored);
        }
    }

    @Test
    void saveCreatesAndThenReplacesStoredInventory() throws Exception {
        UUID playerId = UUID.randomUUID();
        try (PlayerInventoryDao dao = openDao()) {
            dao.save(playerId, new byte[]{1, 2, 3}).get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            assertArrayEquals(
                    new byte[]{1, 2, 3},
                    dao.load(playerId).get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS).orElseThrow()
            );

            dao.save(playerId, new byte[]{9, 8}).get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            assertArrayEquals(
                    new byte[]{9, 8},
                    dao.load(playerId).get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS).orElseThrow()
            );
        }
    }

    @Test
    void closeFlushesQueuedWritesBeforeStoppingWorker() throws Exception {
        Path database = temporaryDirectory.resolve("flush.db");
        UUID playerId = UUID.randomUUID();
        PlayerInventoryDao writer = new PlayerInventoryDao(database);
        writer.initialized().get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        CompletableFuture<Void> queuedWrite = writer.save(playerId, new byte[]{4, 4, 4});

        writer.close();
        queuedWrite.get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

        try (PlayerInventoryDao reader = new PlayerInventoryDao(database)) {
            reader.initialized().get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            assertArrayEquals(
                    new byte[]{4, 4, 4},
                    reader.load(playerId).get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS).orElseThrow()
            );
        }
    }

    @Test
    void newOperationsAreRejectedAfterClose() throws Exception {
        PlayerInventoryDao dao = openDao();
        dao.close();

        CompletableFuture<Optional<byte[]>> rejected = dao.load(UUID.randomUUID());

        assertThrows(
                java.util.concurrent.ExecutionException.class,
                () -> rejected.get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        );
    }

    private PlayerInventoryDao openDao() throws Exception {
        PlayerInventoryDao dao = new PlayerInventoryDao(temporaryDirectory.resolve(UUID.randomUUID() + ".db"));
        dao.initialized().get(WAIT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        return dao;
    }
}
