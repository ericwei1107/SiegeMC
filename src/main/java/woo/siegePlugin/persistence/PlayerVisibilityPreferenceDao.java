package woo.siegePlugin.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Shared persistence for opt-in/out HUD preferences. */
public final class PlayerVisibilityPreferenceDao {

    private final SiegeDatabase database;
    private final String table;

    public PlayerVisibilityPreferenceDao(SiegeDatabase database, String table) {
        if (!table.equals("sidebar_preferences") && !table.equals("bossbar_preferences")) {
            throw new IllegalArgumentException("Unsupported preference table: " + table);
        }
        this.database = database;
        this.table = table;
    }

    public CompletableFuture<Optional<Boolean>> load(UUID playerId) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT visible FROM " + table + " WHERE player_uuid = ?"
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet results = statement.executeQuery()) {
                    return results.next()
                            ? Optional.of(results.getInt("visible") != 0)
                            : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<Void> save(UUID playerId, boolean visible) {
        return database.submitTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO %s (player_uuid, visible, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        visible = excluded.visible,
                        updated_at = excluded.updated_at
                    """.formatted(table))) {
                statement.setString(1, playerId.toString());
                statement.setInt(2, visible ? 1 : 0);
                statement.setLong(3, Instant.now().toEpochMilli());
                statement.executeUpdate();
                return null;
            }
        });
    }
}
