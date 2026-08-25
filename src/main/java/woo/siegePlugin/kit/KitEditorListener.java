package woo.siegePlugin.kit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the kit editor.
 *
 * <p>Every click and drag is cancelled and the working loadout is mutated in
 * code, so the editor's items are never real: shift-click, drag, number-key
 * swaps, closing, dying, and disconnecting all have nothing to duplicate.</p>
 */
public final class KitEditorListener implements Listener {

    private final KitService kitService;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public KitEditorListener(KitService kitService) {
        this.kitService = kitService;
    }

    public void open(Player player) {
        Session session = new Session(kitService.currentLoadout(player));
        sessions.put(player.getUniqueId(), session);
        player.openInventory(KitEditorMenu.create(session.loadout, kitService.profile(), session.selected));
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
        Session session = sessions.get(player.getUniqueId());
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

        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        List<String> problems = kitService.save(player, session.loadout);
        if (problems.isEmpty()) {
            player.sendMessage("Kit saved.");
            return;
        }

        player.sendMessage("Your kit was not saved:");
        problems.forEach(problem -> player.sendMessage(" - " + problem));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        kitService.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Nothing to hand back: the editor never held real items.
        sessions.remove(event.getPlayer().getUniqueId());
        kitService.forget(event.getPlayer());
    }

    private void handleClick(Player player, Session session, Inventory inventory, int guiSlot) {
        if (guiSlot == KitEditorMenu.RESET_SLOT) {
            session.loadout = KitLoadout.defaultFor(kitService.profile());
            session.selected = null;
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

        if (session.loadout.itemAt(inventorySlot) != null) {
            session.loadout.setItemAt(inventorySlot, null);
            redraw(session, inventory);
            return;
        }
        if (session.selected == null) {
            player.sendMessage("Pick an item from the bottom row first.");
            return;
        }

        place(player, session, inventory, inventorySlot, session.selected);
    }

    private void handlePaletteClick(Player player, Session session, Inventory inventory, String palette) {
        if (KitEditorMenu.ARMOUR_SET.equals(palette)) {
            equipArmourSet(session);
            session.selected = null;
            player.sendMessage("Armour set equipped.");
            redraw(session, inventory);
            return;
        }

        session.selected = palette.equals(session.selected) ? null : palette;
        redraw(session, inventory);
    }

    private void place(Player player, Session session, Inventory inventory, int inventorySlot, String material) {
        KitAllowance allowance = kitService.profile().allowanceFor(material).orElse(null);
        if (allowance == null) {
            return;
        }

        if (!allowance.placement().accepts(inventorySlot)) {
            player.sendMessage(material + " does not belong in that slot.");
            return;
        }

        int remaining = kitService.validator().remainingAllowance(session.loadout.describe(), material);
        if (remaining <= 0) {
            player.sendMessage("You already have the maximum number of " + material + ".");
            return;
        }

        int amount = Math.min(allowance.maxPerSlot(), remaining);
        ItemStack stack = KitItems.create(allowance.template(amount));
        if (stack == null) {
            return;
        }

        session.loadout.setItemAt(inventorySlot, stack);
        redraw(session, inventory);
    }

    private void equipArmourSet(Session session) {
        for (KitAllowance allowance : kitService.profile().palette()) {
            KitSlotKind placement = allowance.placement();
            if (placement == KitSlotKind.STORAGE || placement == KitSlotKind.OFFHAND) {
                continue;
            }
            session.loadout.setItemAt(placement.fixedSlot(), KitItems.create(allowance.template(1)));
        }
    }

    private void redraw(Session session, Inventory inventory) {
        KitEditorMenu.render(inventory, session.loadout, kitService.profile(), session.selected);
    }

    private static final class Session {

        private KitLoadout loadout;
        private String selected;

        private Session(KitLoadout loadout) {
            this.loadout = loadout;
        }
    }
}
