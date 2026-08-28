package woo.siegePlugin.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Transactional persistence for stable configured kit-choice ids. */
public final class KitSelectionDao {

    private final SiegeDatabase database;

    public KitSelectionDao(SiegeDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Map<Integer, String>> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return database.submit(connection -> {
            Map<Integer, String> choices = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT slot, choice_key
                    FROM kit_loadout_choices
                    WHERE player_uuid = ?
                    ORDER BY slot
                    """)) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        choices.put(result.getInt("slot"), result.getString("choice_key"));
                    }
                }
            }
            return Map.copyOf(choices);
        });
    }

    /** Replaces one player's entire selection atomically; an empty map resets to defaults. */
    public CompletableFuture<Void> save(UUID playerId, Map<Integer, String> choices) {
        Objects.requireNonNull(playerId, "playerId");
        Map<Integer, String> safeChoices = Map.copyOf(new LinkedHashMap<>(choices));
        return database.submitTransaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM kit_loadout_choices WHERE player_uuid = ?"
            )) {
                delete.setString(1, playerId.toString());
                delete.executeUpdate();
            }

            if (safeChoices.isEmpty()) {
                return null;
            }

            long updatedAt = System.currentTimeMillis();
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO kit_loadout_choices (player_uuid, slot, choice_key, updated_at)
                    VALUES (?, ?, ?, ?)
                    """)) {
                for (Map.Entry<Integer, String> choice : safeChoices.entrySet()) {
                    insert.setString(1, playerId.toString());
                    insert.setInt(2, choice.getKey());
                    insert.setString(3, choice.getValue());
                    insert.setLong(4, updatedAt);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            return null;
        });
    }
}
