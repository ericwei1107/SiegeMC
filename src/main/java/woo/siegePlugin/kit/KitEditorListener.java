package woo.siegePlugin.kit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.time.Duration;

/** Drives the trusted launcher/editor workflow and owns temporary editing sessions. */
public final class KitEditorListener implements Listener {

    private final KitService kitService;
    private final KitCommandCooldown cooldown;
    private final KitEditSessionStore sessions = new KitEditSessionStore();

    public KitEditorListener(KitService kitService, KitCommandCooldown cooldown) {
        this.kitService = kitService;
        this.cooldown = cooldown;
    }

    public void open(Player player) {
        player.openInventory(KitEditorMenu.launcher(kitService));
    }

    public void shutdown() {
        sessions.clear();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof KitEditorMenu.BaseHolder)) {
            return;
        }

        // Cancel first: this also blocks shift-clicks and hotbar swaps originating below the GUI.
        event.setCancelled(true);
        if (!top.equals(event.getClickedInventory()) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getSlot();
        if (holder instanceof KitEditorMenu.LauncherHolder) {
            handleLauncherClick(player, slot);
            return;
        }
        if (!(holder instanceof KitEditorMenu.SessionHolder sessionHolder)) {
            return;
        }
        KitEditSessionStore.Session session = sessions.get(player.getUniqueId(), sessionHolder.generation());
        if (session == null) {
            return;
        }
        if (holder instanceof KitEditorMenu.EditorHolder editorHolder) {
            handleEditorClick(player, session, editorHolder, slot);
        } else if (holder instanceof KitEditorMenu.ChoiceHolder choiceHolder) {
            handleChoiceClick(player, session, choiceHolder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof KitEditorMenu.BaseHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof KitEditorMenu.SessionHolder holder)) {
            return;
        }
        KitEditSessionStore.Session session = sessions.get(player.getUniqueId(), holder.generation());
        if (session == null || session.consumeSuppressedClose()) {
            return;
        }
        if (session.view() == KitEditSessionStore.View.SAVING) {
            session.markClosedWhileSaving();
            return;
        }
        sessions.finish(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        kitService.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.finish(event.getPlayer().getUniqueId());
        kitService.forget(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        sessions.finish(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        sessions.finish(event.getPlayer().getUniqueId());
    }

    private void handleLauncherClick(Player player, int slot) {
        KitEditSessionStore.Session existing = sessions.get(player.getUniqueId());
        if (existing != null && existing.view() == KitEditSessionStore.View.SAVING) {
            player.sendMessage("Your personalized kit is still being saved. Please wait a moment.");
            return;
        }
        if (slot == KitEditorMenu.LAUNCHER_EQUIP_SLOT) {
            equip(player);
            return;
        }
        if (slot != KitEditorMenu.LAUNCHER_CUSTOMIZE_SLOT) {
            return;
        }
        if (!ensureLoaded(player)) {
            return;
        }
        if (!kitService.hasEditableChoices()) {
            player.sendMessage("Kit customization has not been configured by an administrator.");
            return;
        }
        KitEditSessionStore.Session session = sessions.start(player.getUniqueId(), kitService.currentSelection(player));
        player.openInventory(KitEditorMenu.editor(kitService, session.selection(), session.generation()));
    }

    private void handleEditorClick(
            Player player,
            KitEditSessionStore.Session session,
            KitEditorMenu.EditorHolder holder,
            int slot
    ) {
        if (session.view() != KitEditSessionStore.View.EDITOR) {
            return;
        }
        if (slot == KitEditorMenu.SAVE_SLOT) {
            saveAndEquip(player, session);
            return;
        }
        if (slot == KitEditorMenu.RESET_SLOT) {
            session.setSelection(KitSelection.empty());
            reopenEditor(player, session);
            return;
        }
        if (slot == KitEditorMenu.CANCEL_SLOT) {
            cancel(player);
            return;
        }

        Integer kitSlot = holder.kitSlotAt(slot);
        if (kitSlot == null) {
            return;
        }
        KitChoiceCatalog.ChoiceGroup group = kitService.catalog()
                .compatibleGroupAt(kitSlot, kitService.snapshot())
                .orElse(null);
        if (group == null) {
            player.sendMessage("That kit choice is no longer available.");
            reopenEditor(player, session);
            return;
        }
        session.navigate(KitEditSessionStore.View.CHOICE, kitSlot);
        player.openInventory(KitEditorMenu.choices(
                kitService,
                group,
                session.selection(),
                session.generation()
        ));
    }

    private void handleChoiceClick(
            Player player,
            KitEditSessionStore.Session session,
            KitEditorMenu.ChoiceHolder holder,
            int slot
    ) {
        if (session.view() != KitEditSessionStore.View.CHOICE || session.choiceSlot() != holder.kitSlot()) {
            return;
        }
        if (slot == KitEditorMenu.BACK_SLOT) {
            reopenEditor(player, session);
            return;
        }
        if (slot == KitEditorMenu.CANCEL_SLOT) {
            cancel(player);
            return;
        }

        String choice = holder.choiceAt(slot);
        if (choice == null) {
            return;
        }
        KitChoiceCatalog.ChoiceGroup group = kitService.catalog()
                .compatibleGroupAt(holder.kitSlot(), kitService.snapshot())
                .orElse(null);
        if (group == null || group.choice(choice).isEmpty()) {
            player.sendMessage("That replacement is no longer available.");
            reopenEditor(player, session);
            return;
        }
        session.setSelection(session.selection().withChoice(holder.kitSlot(), choice));
        reopenEditor(player, session);
    }

    private void equip(Player player) {
        if (!ensureLoaded(player)) {
            return;
        }
        Duration remaining = cooldown.remaining(player.getUniqueId());
        if (!remaining.isZero()) {
            player.sendMessage("You must wait " + formatDuration(remaining) + " before receiving another siege kit.");
            return;
        }
        kitService.apply(player);
        cooldown.start(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("Your siege kit has been equipped.");
    }

    private void saveAndEquip(Player player, KitEditSessionStore.Session session) {
        Duration remaining = cooldown.remaining(player.getUniqueId());
        if (!remaining.isZero()) {
            player.sendMessage("You must wait " + formatDuration(remaining) + " before receiving another siege kit.");
            return;
        }
        if (!session.beginSaving()) {
            return;
        }
        player.openInventory(KitEditorMenu.saving(session.generation()));
        kitService.saveSelection(player, session.selection(), result -> {
            KitEditSessionStore.Session current = sessions.get(player.getUniqueId(), session.generation());
            if (current == null || current.view() != KitEditSessionStore.View.SAVING) {
                return;
            }
            if (result.outcome() == KitService.SaveOutcome.SUCCESS) {
                sessions.finish(player.getUniqueId());
                if (player.isOnline()) {
                    player.closeInventory();
                    kitService.apply(player, result.loadout());
                    cooldown.start(player.getUniqueId());
                    player.sendMessage("Your personalized siege kit was saved and equipped.");
                }
                return;
            }

            String message = result.outcome() == KitService.SaveOutcome.INVALID
                    ? "Your kit contains a choice that is no longer allowed. Nothing was saved."
                    : "Your kit could not be saved. Your previous saved kit is unchanged.";
            player.sendMessage(message);
            if (!player.isOnline() || current.closedWhileSaving()) {
                sessions.finish(player.getUniqueId());
                return;
            }
            reopenEditor(player, current);
        });
    }

    private void reopenEditor(Player player, KitEditSessionStore.Session session) {
        session.navigate(KitEditSessionStore.View.EDITOR, -1);
        player.openInventory(KitEditorMenu.editor(kitService, session.selection(), session.generation()));
    }

    private void cancel(Player player) {
        sessions.finish(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("Kit customization cancelled. Nothing was saved.");
    }

    private boolean ensureLoaded(Player player) {
        if (kitService.isLoadReady(player)) {
            return true;
        }
        if (kitService.isLoadFailed(player)) {
            kitService.load(player);
            player.sendMessage("Your saved kit could not be loaded. Retrying now; open /siege kit again shortly.");
        } else {
            player.sendMessage("Your saved kit is still loading. Try again in a moment.");
        }
        return false;
    }

    static String formatDuration(Duration duration) {
        long seconds = Math.max(1L, (duration.toMillis() + 999L) / 1000L);
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes == 0L ? remainder + "s" : minutes + "m " + remainder + "s";
    }
}
