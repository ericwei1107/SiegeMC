package woo.siegePlugin.persistence;

import woo.siegePlugin.title.PlayerTitle;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Durable, UUID-keyed titles for the native player list. */
public final class PlayerTitleDao {

    private final SiegeDatabase database;

    public PlayerTitleDao(SiegeDatabase database) {
        this.database = database;
    }

    public CompletableFuture<Optional<PlayerTitle>> load(UUID playerId) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT title FROM player_titles WHERE player_uuid = ?"
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet results = statement.executeQuery()) {
                    if (!results.next()) {
                        return Optional.empty();
                    }
                    return PlayerTitle.fromStorage(results.getString("title"));
                }
            }
        });
    }

    public CompletableFuture<Void> save(UUID playerId, PlayerTitle title) {
        return database.submitTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_titles (player_uuid, title, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        title = excluded.title,
                        updated_at = excluded.updated_at
                    """)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, title.storageValue());
                statement.setLong(3, Instant.now().toEpochMilli());
                statement.executeUpdate();
                return null;
            }
        });
    }
}
