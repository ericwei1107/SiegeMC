package woo.siegePlugin.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the durable copy of a player's active-siege inventory.
 *
 * <p>Bukkit state must be captured before calling this class; no Bukkit API is
 * accessed from the database thread.</p>
 */
public final class PlayerInventoryDao {

    private final SiegeDatabase database;

    public PlayerInventoryDao(SiegeDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Optional<byte[]>> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return database.submit(connection -> {
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
        return database.submit(connection -> {
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

    public CompletableFuture<Void> clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE players SET stored_inventory = NULL, inventory_updated_at = ? WHERE player_uuid = ?"
            )) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setString(2, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }
}
