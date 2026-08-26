package woo.siegePlugin.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Durable handoff between a currency withdrawal and Bukkit item delivery.
 *
 * <p>A reservation is written in the same transaction as the debit. It is
 * either fulfilled after the server-thread delivery or refunded exactly once
 * during normal failure handling, enable reconciliation, or shutdown.</p>
 */
public final class PurchaseOutboxDao {

    private static final String PENDING = "PENDING";
    private static final String FULFILLED = "FULFILLED";
    private static final String REFUNDED = "REFUNDED";

    private final SiegeDatabase database;

    public PurchaseOutboxDao(SiegeDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** Atomically debits an affordable balance and records a pending delivery. */
    public CompletableFuture<Optional<Reservation>> reserve(UUID playerId, String bundleKey, long price) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(bundleKey, "bundleKey");
        if (price < 0L) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Purchase prices cannot be negative"));
        }

        UUID purchaseId = UUID.randomUUID();
        return database.submitTransaction(connection -> {
            long now = System.currentTimeMillis();
            ensureBalanceRow(connection, playerId, now);
            try (PreparedStatement withdraw = connection.prepareStatement("""
                    UPDATE player_balances
                       SET balance = balance - ?, updated_at = ?
                     WHERE player_uuid = ? AND balance >= ?
                    """)) {
                withdraw.setLong(1, price);
                withdraw.setLong(2, now);
                withdraw.setString(3, playerId.toString());
                withdraw.setLong(4, price);
                if (withdraw.executeUpdate() == 0) {
                    return Optional.empty();
                }
            }

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO purchase_outbox (purchase_id, player_uuid, bundle_key, price, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, purchaseId.toString());
                insert.setString(2, playerId.toString());
                insert.setString(3, bundleKey);
                insert.setLong(4, price);
                insert.setString(5, PENDING);
                insert.setLong(6, now);
                insert.executeUpdate();
            }
            return Optional.of(new Reservation(purchaseId, playerId, bundleKey, price, readBalance(connection, playerId)));
        });
    }

    /** Marks a delivered reservation final. False means it was already refunded. */
    public CompletableFuture<Boolean> markFulfilled(UUID purchaseId) {
        Objects.requireNonNull(purchaseId, "purchaseId");
        return database.submitTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE purchase_outbox
                       SET status = ?, fulfilled_at = ?
                     WHERE purchase_id = ? AND status = ?
                    """)) {
                statement.setString(1, FULFILLED);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, purchaseId.toString());
                statement.setString(4, PENDING);
                return statement.executeUpdate() == 1;
            }
        });
    }

    /** Refunds a still-pending reservation and returns its new balance. */
    public CompletableFuture<Optional<Long>> refund(UUID purchaseId) {
        Objects.requireNonNull(purchaseId, "purchaseId");
        return database.submitTransaction(connection -> refundPending(connection, purchaseId));
    }

    /** Reconciles all unfinished deliveries, normally at enable and shutdown. */
    public CompletableFuture<Integer> refundAllPending() {
        return database.submitTransaction(connection -> {
            int refunded = 0;
            List<UUID> pendingIds = new ArrayList<>();
            try (PreparedStatement pending = connection.prepareStatement(
                    "SELECT purchase_id FROM purchase_outbox WHERE status = ?"
            )) {
                pending.setString(1, PENDING);
                try (ResultSet result = pending.executeQuery()) {
                    while (result.next()) {
                        pendingIds.add(UUID.fromString(result.getString("purchase_id")));
                    }
                }
            }
            for (UUID purchaseId : pendingIds) {
                if (refundPending(connection, purchaseId).isPresent()) {
                    refunded++;
                }
            }
            return refunded;
        });
    }

    private static Optional<Long> refundPending(java.sql.Connection connection, UUID purchaseId) throws SQLException {
        PurchaseRecord purchase = readPendingPurchase(connection, purchaseId);
        if (purchase == null) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        try (PreparedStatement credit = connection.prepareStatement("""
                UPDATE player_balances
                   SET balance = balance + ?, updated_at = ?
                 WHERE player_uuid = ?
                """)) {
            credit.setLong(1, purchase.price());
            credit.setLong(2, now);
            credit.setString(3, purchase.playerId().toString());
            credit.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE purchase_outbox
                   SET status = ?, refunded_at = ?
                 WHERE purchase_id = ? AND status = ?
                """)) {
            update.setString(1, REFUNDED);
            update.setLong(2, now);
            update.setString(3, purchaseId.toString());
            update.setString(4, PENDING);
            if (update.executeUpdate() != 1) {
                throw new SQLException("Pending purchase changed while it was being refunded");
            }
        }
        return Optional.of(readBalance(connection, purchase.playerId()));
    }

    private static PurchaseRecord readPendingPurchase(java.sql.Connection connection, UUID purchaseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_uuid, price
                FROM purchase_outbox
                WHERE purchase_id = ? AND status = ?
                """)) {
            statement.setString(1, purchaseId.toString());
            statement.setString(2, PENDING);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new PurchaseRecord(UUID.fromString(result.getString("player_uuid")), result.getLong("price"));
            }
        }
    }

    private static void ensureBalanceRow(java.sql.Connection connection, UUID playerId, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_balances (player_uuid, balance, updated_at)
                VALUES (?, 0, ?)
                ON CONFLICT(player_uuid) DO NOTHING
                """)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, now);
            statement.executeUpdate();
        }
    }

    private static long readBalance(java.sql.Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM player_balances WHERE player_uuid = ?"
        )) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Balance row disappeared for " + playerId);
                }
                return result.getLong("balance");
            }
        }
    }

    public record Reservation(UUID purchaseId, UUID playerId, String bundleKey, long price, long remainingBalance) {
    }

    private record PurchaseRecord(UUID playerId, long price) {
    }
}
