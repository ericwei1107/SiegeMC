package woo.siegePlugin.economy;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Drives the shop menu.
 *
 * <p>Every click and drag involving the menu is cancelled, so its display items
 * can never be removed, dragged, or shift-clicked into a real inventory.</p>
 */
public final class ShopListener implements Listener {

    private final CurrencyService currencyService;

    public ShopListener(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopMenu.Holder)) {
            return;
        }

        // Cancel first: shift-clicks and hotbar swaps from the player's own
        // inventory also move items into the menu.
        event.setCancelled(true);

        if (!event.getInventory().equals(event.getClickedInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ShopBundle bundle = ShopBundle.atSlot(event.getSlot()).orElse(null);
        if (bundle == null) {
            return;
        }

        currencyService.purchase(player, bundle, outcome -> report(player, bundle, outcome));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopMenu.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        currencyService.loadBalance(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        currencyService.forget(event.getPlayer());
    }

    private void report(Player player, ShopBundle bundle, PurchaseOutcome outcome) {
        switch (outcome) {
            case SUCCESS -> {
                player.sendMessage("Purchased " + bundle.displayName()
                        + " (balance: " + currencyService.cachedBalance(player) + " coins).");
                // The balance lives in the title, which Bukkit cannot change in
                // place — but only reopen if they are still looking at the shop.
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShopMenu.Holder) {
                    ShopMenu.open(player, currencyService);
                }
            }
            case INSUFFICIENT_FUNDS -> player.sendMessage("You cannot afford " + bundle.displayName() + ".");
            case NO_INVENTORY_SPACE -> player.sendMessage("You do not have room for " + bundle.displayName() + ".");
            case ALREADY_PURCHASING -> player.sendMessage("Your previous purchase is still processing.");
            case FAILED -> player.sendMessage("That purchase failed. Please contact an administrator.");
        }
    }
}
