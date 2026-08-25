package woo.siegePlugin.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Owns the durable copy of a player's active-siege inventory.
 *
 * <p>Every JDBC operation is serialized through one dedicated worker. Bukkit
 * state must be captured before calling this class; no Bukkit API is accessed
 * from the database thread.</p>
 */
public final class PlayerInventoryDao implements AutoCloseable {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final String connectionUrl;
    private final ExecutorService executor;
    private final Object lifecycleLock = new Object();
    private final CompletableFuture<Void> initialized;
    private boolean acceptingWork = true;

    // Accessed only by executor's single worker thread.
    private Connection connection;

    public PlayerInventoryDao(Path databasePath) {
        Path absolutePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
        this.connectionUrl = "jdbc:sqlite:" + absolutePath;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SiegeMC-SQLite");
            thread.setDaemon(false);
            return thread;
        });
        this.initialized = CompletableFuture.runAsync(() -> initialize(absolutePath), executor);
    }

    public CompletableFuture<Void> initialized() {
        return initialized;
    }

    public CompletableFuture<Optional<byte[]>> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT stored_inventory FROM players WHERE player_uuid = ?"
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }

                    byte[] storedInventory = result.getBytes("stored_inventory");
                    if (storedInventory == null) {
                        return Optional.empty();
                    }
                    return Optional.of(Arrays.copyOf(storedInventory, storedInventory.length));
                }
            }
        });
    }

    public CompletableFuture<Void> save(UUID playerId, byte[] inventoryData) {
        Objects.requireNonNull(playerId, "playerId");
        byte[] safeData = Arrays.copyOf(
                Objects.requireNonNull(inventoryData, "inventoryData"),
                inventoryData.length
        );
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO players (player_uuid, stored_inventory, inventory_updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        stored_inventory = excluded.stored_inventory,
                        inventory_updated_at = excluded.inventory_updated_at
                    """)) {
                statement.setString(1, playerId.toString());
                statement.setBytes(2, safeData);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public void close() {
        CompletableFuture<Void> closeTask;
        synchronized (lifecycleLock) {
            if (!acceptingWork) {
                return;
            }
            acceptingWork = false;
            // Taking the same lock as submit() guarantees this task is queued
            // after every write that was accepted before shutdown began.
            closeTask = CompletableFuture.runAsync(this::closeConnection, executor);
        }

        try {
            closeTask.get(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while flushing the SiegeMC database", exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Timed out while flushing the SiegeMC database", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Could not flush the SiegeMC database", exception.getCause());
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("SiegeMC database worker did not stop cleanly");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping the SiegeMC database worker", exception);
            }
        }
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new CompletionException("Could not close the SiegeMC database", exception);
        } finally {
            connection = null;
        }
    }

    private void initialize(Path databasePath) {
        try {
            Path parent = databasePath.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(connectionUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS players (
                            player_uuid TEXT PRIMARY KEY,
                            stored_inventory BLOB,
                            inventory_updated_at INTEGER
                        )
                        """);
            }
        } catch (Exception exception) {
            closeAfterInitializationFailure(exception);
            throw new CompletionException("Could not initialize the SiegeMC database", exception);
        }
    }

    private void closeAfterInitializationFailure(Exception originalFailure) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            originalFailure.addSuppressed(closeFailure);
        } finally {
            connection = null;
        }
    }

    private <T> CompletableFuture<T> submit(SqlOperation<T> operation) {
        synchronized (lifecycleLock) {
            if (!acceptingWork) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("The SiegeMC database is shutting down")
                );
            }

            return CompletableFuture.supplyAsync(() -> {
                initialized.join();
                try {
                    return operation.run();
                } catch (SQLException exception) {
                    throw new CompletionException(exception);
                }
            }, executor);
        }
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run() throws SQLException;
    }
}
