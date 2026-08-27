package woo.siegePlugin.economy;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.persistence.PlayerBalanceDao;
import woo.siegePlugin.persistence.PurchaseOutboxDao;
import woo.siegePlugin.minecart.SiegeMinecartMarker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Siege currency: durable balances plus the shop's purchase flow.
 *
 * <p>The database is authoritative for spending — a conditional withdraw is
 * what prevents overdrafts. The cached balance exists only so menus can render
 * without waiting on a query.</p>
 */
public final class CurrencyService {

    private final JavaPlugin plugin;
    private final PlayerBalanceDao balanceDao;
    private final PurchaseOutboxDao purchaseOutbox;
    private final CurrencySettings settings;
    private final SiegeMinecartMarker minecartMarker;
    private final Map<UUID, Long> cachedBalances = new HashMap<>();
    private final Set<UUID> purchasesInFlight = new HashSet<>();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean acceptingPurchases = new AtomicBoolean(false);

    public CurrencyService(
            JavaPlugin plugin,
            PlayerBalanceDao balanceDao,
            PurchaseOutboxDao purchaseOutbox,
            CurrencySettings settings,
            SiegeMinecartMarker minecartMarker
    ) {
        this.plugin = plugin;
        this.balanceDao = balanceDao;
        this.purchaseOutbox = purchaseOutbox;
        this.settings = settings;
        this.minecartMarker = minecartMarker;
    }

    public void shutdown() {
        acceptingPurchases.set(false);
        try {
            // All previously accepted database operations are queued before
            // this reconciliation on SiegeDatabase's single worker. Pending
            // debits therefore become durable refunds before close() flushes.
            purchaseOutbox.refundAllPending().join();
        } catch (RuntimeException exception) {
            logFailure("reconcile pending purchases during shutdown", exception);
        }
        active.set(false);
        cachedBalances.clear();
        purchasesInFlight.clear();
    }

    /** Reconciles a crash-leftover reservation before opening the shop. */
    public void start() {
        purchaseOutbox.refundAllPending().whenComplete((refunded, failure) -> onServerThread(() -> {
            if (failure != null) {
                logFailure("reconcile pending purchases on enable", failure);
                return;
            }
            acceptingPurchases.set(true);
            if (refunded > 0) {
                plugin.getLogger().warning("Refunded " + refunded + " unfinished shop purchase(s) from a prior shutdown.");
            }
            loadOnlineBalances();
        }));
    }

    public CurrencySettings settings() {
        return settings;
    }

    /** Best-known balance for display. Never used to authorise a purchase. */
    public long cachedBalance(Player player) {
        return cachedBalances.getOrDefault(player.getUniqueId(), 0L);
    }

    /** Covers players already connected when the plugin enables or reloads. */
    public void loadOnlineBalances() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            loadBalance(player);
        }
    }

    public void loadBalance(Player player) {
        UUID playerId = player.getUniqueId();
        balanceDao.load(playerId).whenComplete((balance, failure) -> onServerThread(() -> {
            if (failure != null) {
                logFailure("load the balance for " + player.getName(), failure);
                return;
            }
            cachedBalances.put(playerId, balance);
        }));
    }

    public void forget(Player player) {
        cachedBalances.remove(player.getUniqueId());
        purchasesInFlight.remove(player.getUniqueId());
    }

    /** Credits one completed banner controller for an active scoring tick. */
    public void awardBannerControlTick(Player player) {
        award(player, settings.perCaptureTick(), "holding the banner");
    }

    public void awardKill(Player killer) {
        award(killer, settings.perKill(), "a kill");
    }

    private void award(Player player, long amount, String reason) {
        if (amount <= 0L) {
            return;
        }

        UUID playerId = player.getUniqueId();
        balanceDao.deposit(playerId, amount).whenComplete((balance, failure) -> onServerThread(() -> {
            if (failure != null) {
                logFailure("credit " + amount + " to " + player.getName(), failure);
                return;
            }
            cachedBalances.put(playerId, balance);
            if (player.isOnline()) {
                player.sendMessage("+" + amount + " siege coins for " + reason + " (balance: " + balance + ").");
            }
        }));
    }

    /**
     * Buys one bundle. Space is checked before the withdraw to fail fast, and
     * again afterwards because the inventory can change while the write is in
     * flight; if it no longer fits, the price is refunded.
     */
    public void purchase(Player player, ShopBundle bundle, Consumer<PurchaseOutcome> outcome) {
        if (!acceptingPurchases.get()) {
            outcome.accept(PurchaseOutcome.FAILED);
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!purchasesInFlight.add(playerId)) {
            outcome.accept(PurchaseOutcome.ALREADY_PURCHASING);
            return;
        }

        ItemStack item = bundle.createItem();
        if (bundle == ShopBundle.TNT_MINECART) {
            minecartMarker.mark(item);
        }
        if (!InventorySpace.hasRoomFor(player.getInventory(), item)) {
            purchasesInFlight.remove(playerId);
            outcome.accept(PurchaseOutcome.NO_INVENTORY_SPACE);
            return;
        }

        long price = settings.priceOf(bundle);
        purchaseOutbox.reserve(playerId, bundle.name(), price).whenComplete((reservation, failure) -> onServerThread(() -> {
            purchasesInFlight.remove(playerId);

            if (failure != null) {
                logFailure("withdraw " + price + " from " + player.getName(), failure);
                outcome.accept(PurchaseOutcome.FAILED);
                return;
            }
            if (reservation.isEmpty()) {
                outcome.accept(PurchaseOutcome.INSUFFICIENT_FUNDS);
                return;
            }

            PurchaseOutboxDao.Reservation accepted = reservation.orElseThrow();
            cachedBalances.put(playerId, accepted.remainingBalance());
            if (!acceptingPurchases.get() || !player.isOnline() || !InventorySpace.hasRoomFor(player.getInventory(), item)) {
                refund(accepted);
                outcome.accept(PurchaseOutcome.NO_INVENTORY_SPACE);
                return;
            }

            // Space was just verified, but never let a paid-for item vanish.
            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            purchaseOutbox.markFulfilled(accepted.purchaseId()).whenComplete((fulfilled, markFailure) -> {
                if (markFailure != null) {
                    logFailure("mark delivered purchase " + accepted.purchaseId() + " fulfilled", markFailure);
                } else if (!fulfilled) {
                    plugin.getLogger().severe("Delivered purchase " + accepted.purchaseId()
                            + " was already reconciled instead of being marked fulfilled.");
                }
            });
            outcome.accept(PurchaseOutcome.SUCCESS);
        }));
    }

    private void refund(PurchaseOutboxDao.Reservation reservation) {
        purchaseOutbox.refund(reservation.purchaseId()).whenComplete((balance, failure) -> onServerThread(() -> {
            if (failure != null) {
                // The player was charged and got nothing; this must be visible.
                logFailure("refund purchase " + reservation.purchaseId(), failure);
                return;
            }
            balance.ifPresent(refundedBalance -> cachedBalances.put(reservation.playerId(), refundedBalance));
        }));
    }

    private void onServerThread(Runnable action) {
        if (!active.get()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (active.get()) {
                action.run();
            }
        });
    }

    private void logFailure(String what, Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        plugin.getLogger().log(Level.SEVERE, "Could not " + what + ".", cause);
    }
}
