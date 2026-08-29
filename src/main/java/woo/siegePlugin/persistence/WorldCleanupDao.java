package woo.siegePlugin.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Durable record of generated world copies that still need deleting.
 *
 * <p>A copy is enrolled here <em>before</em> any unload or delete is attempted,
 * so a crash mid-cleanup leaves a row to retry rather than an orphaned folder
 * nobody remembers. Retries use capped backoff because the usual reason a
 * delete fails is that a player has not left the world yet.</p>
 */
public final class WorldCleanupDao {

    private final SiegeDatabase database;

    public WorldCleanupDao(SiegeDatabase database) {
        this.database = database;
    }

    public CompletableFuture<Void> enroll(String worldName, String folder) {
        return database.submit(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO generated_world_cleanup (
                        world_name, folder, attempts, last_error, next_retry_at, created_at
                    ) VALUES (?, ?, 0, NULL, ?, ?)
                    ON CONFLICT(world_name) DO UPDATE SET folder = excluded.folder
                    """)) {
                long now = System.currentTimeMillis();
                insert.setString(1, worldName);
                insert.setString(2, folder);
                insert.setLong(3, now);
                insert.setLong(4, now);
                insert.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> complete(String worldName) {
        return database.submit(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM generated_world_cleanup WHERE world_name = ?"
            )) {
                delete.setString(1, worldName);
                delete.executeUpdate();
            }
            return null;
        });
    }

    /** Records a failed attempt and schedules the next one with capped backoff. */
    public CompletableFuture<Void> recordFailure(String worldName, String error, long nextRetryAtMillis) {
        return database.submit(connection -> {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE generated_world_cleanup
                    SET attempts = attempts + 1, last_error = ?, next_retry_at = ?
                    WHERE world_name = ?
                    """)) {
                update.setString(1, truncate(error));
                update.setLong(2, nextRetryAtMillis);
                update.setString(3, worldName);
                update.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<PendingCleanup>> due(Instant now) {
        return database.submit(connection -> {
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT world_name, folder, attempts, last_error, next_retry_at
                    FROM generated_world_cleanup
                    WHERE next_retry_at <= ?
                    ORDER BY created_at
                    """)) {
                query.setLong(1, now.toEpochMilli());
                try (ResultSet result = query.executeQuery()) {
                    List<PendingCleanup> pending = new ArrayList<>();
                    while (result.next()) {
                        pending.add(new PendingCleanup(
                                result.getString("world_name"),
                                result.getString("folder"),
                                result.getInt("attempts"),
                                result.getString("last_error")
                        ));
                    }
                    return List.copyOf(pending);
                }
            }
        });
    }

    public CompletableFuture<List<PendingCleanup>> all() {
        return due(Instant.ofEpochMilli(Long.MAX_VALUE));
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        String collapsed = error.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 200 ? collapsed : collapsed.substring(0, 199) + "…";
    }

    public record PendingCleanup(String worldName, String folder, int attempts, String lastError) {
    }
}
