package woo.siegePlugin.kit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Drives the kit editor.
 *
 * <p>Every click and drag is cancelled and the working loadout is mutated in
 * code, so the editor's items are never real: shift-click, drag, number-key
 * swaps, closing, dying, and disconnecting all have nothing to duplicate.</p>
 */
public final class KitEditorListener implements Listener {

    private final KitService kitService;
    private final KitEditSessionStore sessions = new KitEditSessionStore();

    public KitEditorListener(KitService kitService) {
        this.kitService = kitService;
    }

    public boolean open(Player player) {
        if (!kitService.isLoadReady(player)) {
            player.sendMessage("Your saved kit is still loading. Please try again in a moment.");
            return false;
        }
        KitEditSessionStore.Session session = sessions.start(player.getUniqueId(), kitService.currentLoadout(player));
        player.openInventory(KitEditorMenu.create(session.loadout(), kitService.profile(), session.selected()));
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitEditorMenu.Holder)) {
            return;
        }

        // Cancelled unconditionally: this also covers shift-clicks and hotbar
        // swaps originating in the player's own inventory.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        KitEditSessionStore.Session session = sessions.get(player.getUniqueId());
        if (session == null || !event.getInventory().equals(event.getClickedInventory())) {
            return;
        }

        handleClick(player, session, event.getInventory(), event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof KitEditorMenu.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitEditorMenu.Holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        finishEditing(player, KitEditSessionStore.EndCause.INVENTORY_CLOSE, true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        kitService.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        finishEditing(event.getPlayer(), KitEditSessionStore.EndCause.DISCONNECT, false);
        kitService.forget(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        finishEditing(event.getPlayer(), KitEditSessionStore.EndCause.DEATH, false);
    }

    private void handleClick(Player player, KitEditSessionStore.Session session, Inventory inventory, int guiSlot) {
        if (guiSlot == KitEditorMenu.RESET_SLOT) {
            session.setLoadout(KitLoadout.defaultFor(kitService.profile()));
            session.setSelected(null);
            player.sendMessage("Kit reset to the default loadout.");
            redraw(session, inventory);
            return;
        }

        String palette = KitEditorMenu.paletteSelectionAt(guiSlot);
        if (palette != null) {
            handlePaletteClick(player, session, inventory, palette);
            return;
        }

        int inventorySlot = KitEditorMenu.inventorySlotFor(guiSlot);
        if (inventorySlot < 0) {
            return;
        }

        if (session.loadout().itemAt(inventorySlot) != null) {
            session.loadout().setItemAt(inventorySlot, null);
            redraw(session, inventory);
            return;
        }
        if (session.selected() == null) {
            player.sendMessage("Pick an item from the bottom row first.");
            return;
        }

        place(player, session, inventory, inventorySlot, session.selected());
    }

    private void handlePaletteClick(
            Player player,
            KitEditSessionStore.Session session,
            Inventory inventory,
            String palette
    ) {
        if (KitEditorMenu.ARMOUR_SET.equals(palette)) {
            equipArmourSet(session);
            session.setSelected(null);
            player.sendMessage("Armour set equipped.");
            redraw(session, inventory);
            return;
        }

        session.setSelected(palette.equals(session.selected()) ? null : palette);
        redraw(session, inventory);
    }

    private void place(
            Player player,
            KitEditSessionStore.Session session,
            Inventory inventory,
            int inventorySlot,
            String allowanceKey
    ) {
        KitAllowance allowance = kitService.profile().allowanceForKey(allowanceKey).orElse(null);
        if (allowance == null) {
            return;
        }

        if (!allowance.placement().accepts(inventorySlot)) {
            player.sendMessage(allowance.material() + " does not belong in that slot.");
            return;
        }

        int remaining = kitService.validator().remainingAllowance(session.loadout().describe(), allowanceKey);
        if (remaining <= 0) {
            player.sendMessage("You already have the maximum number of " + allowance.material() + ".");
            return;
        }

        int amount = Math.min(allowance.maxPerSlot(), remaining);
        ItemStack stack = KitItems.create(allowance.template(amount));
        if (stack == null) {
            return;
        }

        session.loadout().setItemAt(inventorySlot, stack);
        redraw(session, inventory);
    }

    private void equipArmourSet(KitEditSessionStore.Session session) {
        for (KitAllowance allowance : kitService.profile().palette()) {
            KitSlotKind placement = allowance.placement();
            if (placement == KitSlotKind.STORAGE || placement == KitSlotKind.OFFHAND) {
                continue;
            }
            session.loadout().setItemAt(placement.fixedSlot(), KitItems.create(allowance.template(1)));
        }
    }

    private void redraw(KitEditSessionStore.Session session, Inventory inventory) {
        KitEditorMenu.render(inventory, session.loadout(), kitService.profile(), session.selected());
    }

    /**
     * All terminal editor paths use this same virtual-session handoff. The
     * editor never owns physical items, so saving the draft exactly once is
     * lossless for close, death, and disconnect alike.
     */
    private void finishEditing(Player player, KitEditSessionStore.EndCause cause, boolean notifyPlayer) {
        KitEditSessionStore.Session session = sessions.finish(player.getUniqueId(), cause).orElse(null);
        if (session == null) {
            return;
        }

        List<String> problems = kitService.save(player, session.loadout());
        if (problems.isEmpty()) {
            if (notifyPlayer) {
                player.sendMessage("Kit saved.");
            }
            return;
        }

        if (notifyPlayer) {
            player.sendMessage("Your kit was not saved:");
            problems.forEach(problem -> player.sendMessage(" - " + problem));
        }
    }

}
