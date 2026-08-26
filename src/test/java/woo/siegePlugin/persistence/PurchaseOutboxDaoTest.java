package woo.siegePlugin.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseOutboxDaoTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @TempDir
    Path temporaryDirectory;

    @Test
    void reservationDebitsAndRefundsExactlyOnce() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao balances = new PlayerBalanceDao(database);
            PurchaseOutboxDao outbox = new PurchaseOutboxDao(database);
            UUID playerId = UUID.randomUUID();
            await(balances.deposit(playerId, 100L));

            PurchaseOutboxDao.Reservation reservation = await(outbox.reserve(playerId, "COBBLESTONE", 60L))
                    .orElseThrow();

            assertEquals(40L, reservation.remainingBalance());
            assertEquals(40L, await(balances.load(playerId)));
            assertEquals(Optional.of(100L), await(outbox.refund(reservation.purchaseId())));
            assertTrue(await(outbox.refund(reservation.purchaseId())).isEmpty());
            assertEquals(100L, await(balances.load(playerId)));
        }
    }

    @Test
    void shutdownReconciliationRefundsAnAcceptedButUndeliveredPurchase() throws Exception {
        Path databasePath = temporaryDirectory.resolve("shutdown-race.db");
        UUID playerId = UUID.randomUUID();

        try (SiegeDatabase database = new SiegeDatabase(databasePath)) {
            await(database.initialized());
            PlayerBalanceDao balances = new PlayerBalanceDao(database);
            PurchaseOutboxDao outbox = new PurchaseOutboxDao(database);
            await(balances.deposit(playerId, 100L));
            assertTrue(await(outbox.reserve(playerId, "TNT_MINECART", 75L)).isPresent());

            // This is the deterministic equivalent of disable happening after
            // the debit commits but before Bukkit receives the delivery task.
            assertEquals(1, await(outbox.refundAllPending()));
            assertEquals(100L, await(balances.load(playerId)));
        }

        try (SiegeDatabase reopened = new SiegeDatabase(databasePath)) {
            await(reopened.initialized());
            PlayerBalanceDao balances = new PlayerBalanceDao(reopened);
            PurchaseOutboxDao outbox = new PurchaseOutboxDao(reopened);

            assertEquals(100L, await(balances.load(playerId)));
            assertEquals(0, await(outbox.refundAllPending()));
        }
    }

    @Test
    void fulfilledPurchaseIsNotRefundedDuringLaterReconciliation() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao balances = new PlayerBalanceDao(database);
            PurchaseOutboxDao outbox = new PurchaseOutboxDao(database);
            UUID playerId = UUID.randomUUID();
            await(balances.deposit(playerId, 80L));
            PurchaseOutboxDao.Reservation reservation = await(outbox.reserve(playerId, "ARROWS", 30L)).orElseThrow();

            assertTrue(await(outbox.markFulfilled(reservation.purchaseId())));
            assertEquals(0, await(outbox.refundAllPending()));
            assertEquals(50L, await(balances.load(playerId)));
            assertFalse(await(outbox.markFulfilled(reservation.purchaseId())));
        }
    }

    @Test
    void zeroPricedPlaceholderBundleCanBeReservedForANewPlayer() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao balances = new PlayerBalanceDao(database);
            PurchaseOutboxDao outbox = new PurchaseOutboxDao(database);
            UUID playerId = UUID.randomUUID();

            PurchaseOutboxDao.Reservation reservation = await(outbox.reserve(playerId, "COBBLESTONE", 0L))
                    .orElseThrow();

            assertEquals(0L, reservation.remainingBalance());
            assertTrue(await(outbox.markFulfilled(reservation.purchaseId())));
            assertEquals(0L, await(balances.load(playerId)));
        }
    }

    private SiegeDatabase openDatabase() throws Exception {
        SiegeDatabase database = new SiegeDatabase(temporaryDirectory.resolve(UUID.randomUUID() + ".db"));
        await(database.initialized());
        return database;
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }
}
