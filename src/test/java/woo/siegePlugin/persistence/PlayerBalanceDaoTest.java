package woo.siegePlugin.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerBalanceDaoTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @TempDir
    Path temporaryDirectory;

    @Test
    void anUnknownPlayerStartsAtZero() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            assertEquals(0L, await(new PlayerBalanceDao(database).load(UUID.randomUUID())));
        }
    }

    @Test
    void depositsAccumulate() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao dao = new PlayerBalanceDao(database);
            UUID player = UUID.randomUUID();

            assertEquals(50L, await(dao.deposit(player, 50L)));
            assertEquals(75L, await(dao.deposit(player, 25L)));
            assertEquals(75L, await(dao.load(player)));
        }
    }

    @Test
    void batchDepositCreditsEveryControllerInOneDurableOperation() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao dao = new PlayerBalanceDao(database);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            await(dao.deposit(first, 10L));

            Map<UUID, Long> balances = await(dao.depositAll(Set.of(first, second), 3L));

            assertEquals(13L, balances.get(first));
            assertEquals(3L, balances.get(second));
            assertEquals(13L, await(dao.load(first)));
            assertEquals(3L, await(dao.load(second)));
        }
    }

    @Test
    void withdrawSucceedsWhenAffordableAndReturnsTheRemainder() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao dao = new PlayerBalanceDao(database);
            UUID player = UUID.randomUUID();
            await(dao.deposit(player, 100L));

            assertEquals(OptionalLong.of(40L), await(dao.tryWithdraw(player, 60L)));
            assertEquals(40L, await(dao.load(player)));
        }
    }

    @Test
    void withdrawOfTheExactBalanceIsAllowed() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao dao = new PlayerBalanceDao(database);
            UUID player = UUID.randomUUID();
            await(dao.deposit(player, 100L));

            assertEquals(OptionalLong.of(0L), await(dao.tryWithdraw(player, 100L)));
        }
    }

    @Test
    void withdrawIsRefusedAndLeavesTheBalanceUntouched() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao dao = new PlayerBalanceDao(database);
            UUID player = UUID.randomUUID();
            await(dao.deposit(player, 30L));

            assertTrue(await(dao.tryWithdraw(player, 31L)).isEmpty());
            assertEquals(30L, await(dao.load(player)));
        }
    }

    @Test
    void withdrawFromAPlayerWithNoRowIsRefused() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao dao = new PlayerBalanceDao(database);

            assertTrue(await(dao.tryWithdraw(UUID.randomUUID(), 1L)).isEmpty());
        }
    }

    @Test
    void concurrentWithdrawsCanNeverOverdraw() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao dao = new PlayerBalanceDao(database);
            UUID player = UUID.randomUUID();
            await(dao.deposit(player, 100L));

            // Five simultaneous 30-coin buys against a 100-coin balance.
            List<CompletableFuture<OptionalLong>> attempts = new ArrayList<>();
            for (int attempt = 0; attempt < 5; attempt++) {
                attempts.add(dao.tryWithdraw(player, 30L));
            }

            long succeeded = 0L;
            for (CompletableFuture<OptionalLong> attempt : attempts) {
                if (await(attempt).isPresent()) {
                    succeeded++;
                }
            }

            assertEquals(3L, succeeded);
            assertEquals(10L, await(dao.load(player)));
        }
    }

    @Test
    void balancesSurviveReopeningTheDatabase() throws Exception {
        Path databasePath = temporaryDirectory.resolve("balances.db");
        UUID player = UUID.randomUUID();
        try (SiegeDatabase database = new SiegeDatabase(databasePath)) {
            await(database.initialized());
            await(new PlayerBalanceDao(database).deposit(player, 250L));
        }

        try (SiegeDatabase reopened = new SiegeDatabase(databasePath)) {
            await(reopened.initialized());
            assertEquals(250L, await(new PlayerBalanceDao(reopened).load(player)));
        }
    }

    @Test
    void negativeAmountsAreRejected() throws Exception {
        try (SiegeDatabase database = openDatabase()) {
            PlayerBalanceDao dao = new PlayerBalanceDao(database);
            UUID player = UUID.randomUUID();

            assertThrows(ExecutionException.class, () -> await(dao.deposit(player, -1L)));
            assertThrows(ExecutionException.class, () -> await(dao.tryWithdraw(player, -1L)));
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
