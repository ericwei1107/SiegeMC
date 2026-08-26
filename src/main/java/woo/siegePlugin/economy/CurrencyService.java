package woo.siegePlugin.economy;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.persistence.PlayerBalanceDao;

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
    private final CurrencySettings settings;
    private final Map<UUID, Long> cachedBalances = new HashMap<>();
    private final Set<UUID> purchasesInFlight = new HashSet<>();
    private final AtomicBoolean active = new AtomicBoolean(true);

    public CurrencyService(JavaPlugin plugin, PlayerBalanceDao balanceDao, CurrencySettings settings) {
        this.plugin = plugin;
        this.balanceDao = balanceDao;
        this.settings = settings;
    }

    public void shutdown() {
        active.set(false);
        cachedBalances.clear();
        purchasesInFlight.clear();
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

    public void awardBannerCapture(Player player) {
        // Stage 4.4j moves this rate to the scoring-tick recipient path.
        award(player, settings.perCaptureTick(), "capturing the banner");
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
        UUID playerId = player.getUniqueId();
        if (!purchasesInFlight.add(playerId)) {
            outcome.accept(PurchaseOutcome.ALREADY_PURCHASING);
            return;
        }

        ItemStack item = bundle.createItem();
        if (!InventorySpace.hasRoomFor(player.getInventory(), item)) {
            purchasesInFlight.remove(playerId);
            outcome.accept(PurchaseOutcome.NO_INVENTORY_SPACE);
            return;
        }

        long price = settings.priceOf(bundle);
        balanceDao.tryWithdraw(playerId, price).whenComplete((remaining, failure) -> onServerThread(() -> {
            purchasesInFlight.remove(playerId);

            if (failure != null) {
                logFailure("withdraw " + price + " from " + player.getName(), failure);
                outcome.accept(PurchaseOutcome.FAILED);
                return;
            }
            if (remaining.isEmpty()) {
                outcome.accept(PurchaseOutcome.INSUFFICIENT_FUNDS);
                return;
            }

            cachedBalances.put(playerId, remaining.getAsLong());
            if (!player.isOnline() || !InventorySpace.hasRoomFor(player.getInventory(), item)) {
                refund(player, price);
                outcome.accept(PurchaseOutcome.NO_INVENTORY_SPACE);
                return;
            }

            // Space was just verified, but never let a paid-for item vanish.
            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            outcome.accept(PurchaseOutcome.SUCCESS);
        }));
    }

    private void refund(Player player, long price) {
        balanceDao.deposit(player.getUniqueId(), price).whenComplete((balance, failure) -> onServerThread(() -> {
            if (failure != null) {
                // The player was charged and got nothing; this must be visible.
                logFailure("refund " + price + " to " + player.getName(), failure);
                return;
            }
            cachedBalances.put(player.getUniqueId(), balance);
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
