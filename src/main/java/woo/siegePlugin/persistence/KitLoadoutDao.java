package woo.siegePlugin.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** One saved kit loadout per player. */
public final class KitLoadoutDao {

    private final SiegeDatabase database;

    public KitLoadoutDao(SiegeDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Optional<byte[]>> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT loadout FROM kit_loadouts WHERE player_uuid = ?"
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }
                    byte[] loadout = result.getBytes("loadout");
                    if (loadout == null) {
                        return Optional.empty();
                    }
                    return Optional.of(Arrays.copyOf(loadout, loadout.length));
                }
            }
        });
    }

    public CompletableFuture<Void> save(UUID playerId, byte[] loadout) {
        Objects.requireNonNull(playerId, "playerId");
        byte[] safeData = Arrays.copyOf(Objects.requireNonNull(loadout, "loadout"), loadout.length);
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO kit_loadouts (player_uuid, loadout, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        loadout = excluded.loadout,
                        updated_at = excluded.updated_at
                    """)) {
                statement.setString(1, playerId.toString());
                statement.setBytes(2, safeData);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
                return null;
            }
        });
    }
}
