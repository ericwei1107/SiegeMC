package woo.siegePlugin.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Durable siege currency balances.
 *
 * <p>Withdrawals are conditional inside a transaction, so a balance can never
 * go negative no matter how fast purchases are attempted.</p>
 */
public final class PlayerBalanceDao {

    private final SiegeDatabase database;

    public PlayerBalanceDao(SiegeDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<Long> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return database.submit(connection -> readBalance(connection, playerId));
    }

    /** Adds to a balance, creating the row when the player is new. */
    public CompletableFuture<Long> deposit(UUID playerId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Deposits cannot be negative"));
        }

        return database.submitTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_balances (player_uuid, balance, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        balance = balance + excluded.balance,
                        updated_at = excluded.updated_at
                    """)) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, amount);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return readBalance(connection, playerId);
        });
    }

    /**
     * Credits several controllers in one SQLite transaction. Banner scoring
     * invokes this once per scoring interval instead of submitting one queued
     * transaction for every controller.
     */
    public CompletableFuture<Map<UUID, Long>> depositAll(Set<UUID> playerIds, long amount) {
        Objects.requireNonNull(playerIds, "playerIds");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Deposits cannot be negative"));
        }
        if (playerIds.isEmpty() || amount == 0L) {
            return CompletableFuture.completedFuture(Map.of());
        }
        Set<UUID> ids = Set.copyOf(playerIds);
        return database.submitTransaction(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_balances (player_uuid, balance, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        balance = balance + excluded.balance,
                        updated_at = excluded.updated_at
                    """)) {
                for (UUID playerId : ids) {
                    statement.setString(1, playerId.toString());
                    statement.setLong(2, amount);
                    statement.setLong(3, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            Map<UUID, Long> balances = new LinkedHashMap<>();
            for (UUID playerId : ids) {
                balances.put(playerId, readBalance(connection, playerId));
            }
            return Map.copyOf(balances);
        });
    }

    /**
     * Deducts only if the balance covers it. Returns the new balance, or empty
     * when the player could not afford the amount.
     */
    public CompletableFuture<OptionalLong> tryWithdraw(UUID playerId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        if (amount < 0L) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Withdrawals cannot be negative"));
        }

        return database.submitTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE player_balances
                       SET balance = balance - ?, updated_at = ?
                     WHERE player_uuid = ? AND balance >= ?
                    """)) {
                statement.setLong(1, amount);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, playerId.toString());
                statement.setLong(4, amount);
                if (statement.executeUpdate() == 0) {
                    return OptionalLong.empty();
                }
            }
            return OptionalLong.of(readBalance(connection, playerId));
        });
    }

    private static long readBalance(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM player_balances WHERE player_uuid = ?"
        )) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("balance") : 0L;
            }
        }
    }
}
