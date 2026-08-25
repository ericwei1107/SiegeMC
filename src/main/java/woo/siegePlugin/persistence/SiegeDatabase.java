package woo.siegePlugin.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The single SQLite connection every DAO shares.
 *
 * <p>All JDBC work is serialized through one worker thread, which is what lets
 * {@link #submitTransaction} span multiple tables safely. Bukkit state must be
 * captured before calling any DAO; no Bukkit API is touched from this thread.</p>
 */
public final class SiegeDatabase implements AutoCloseable {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final String connectionUrl;
    private final ExecutorService executor;
    private final Object lifecycleLock = new Object();
    private final CompletableFuture<Void> initialized;
    private boolean acceptingWork = true;

    // Accessed only by the executor's single worker thread.
    private Connection connection;

    public SiegeDatabase(Path databasePath) {
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

    <T> CompletableFuture<T> submit(SqlOperation<T> operation) {
        synchronized (lifecycleLock) {
            if (!acceptingWork) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("The SiegeMC database is shutting down")
                );
            }

            return CompletableFuture.supplyAsync(() -> {
                initialized.join();
                try {
                    return operation.run(connection);
                } catch (SQLException exception) {
                    throw new CompletionException(exception);
                }
            }, executor);
        }
    }

    /**
     * Runs an operation inside one transaction, so a match total and its ledger
     * entry are never persisted apart.
     */
    <T> CompletableFuture<T> submitTransaction(SqlOperation<T> operation) {
        return submit(activeConnection -> {
            activeConnection.setAutoCommit(false);
            try {
                T result = operation.run(activeConnection);
                activeConnection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    activeConnection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                activeConnection.setAutoCommit(true);
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
        } catch (ExecutionException exception) {
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
                Files.createDirectories(parent);
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(connectionUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS players (
                            player_uuid TEXT PRIMARY KEY,
                            stored_inventory BLOB,
                            inventory_updated_at INTEGER
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS matches (
                            match_id TEXT PRIMARY KEY,
                            red_score INTEGER NOT NULL DEFAULT 0,
                            blue_score INTEGER NOT NULL DEFAULT 0,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS score_ledger (
                            entry_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            match_id TEXT NOT NULL REFERENCES matches(match_id),
                            team TEXT NOT NULL,
                            points INTEGER NOT NULL,
                            reason TEXT NOT NULL,
                            created_at INTEGER NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_score_ledger_match
                            ON score_ledger(match_id, entry_id)
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

    @FunctionalInterface
    interface SqlOperation<T> {
        T run(Connection connection) throws SQLException;
    }
}
