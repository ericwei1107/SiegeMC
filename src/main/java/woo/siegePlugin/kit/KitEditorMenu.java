package woo.siegePlugin.kit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates the launcher, editor, replacement-choice, and saving inventories. */
public final class KitEditorMenu {

    public static final int LAUNCHER_SIZE = 27;
    public static final int LAUNCHER_EQUIP_SLOT = 11;
    public static final int LAUNCHER_CUSTOMIZE_SLOT = 15;
    public static final int EDITOR_SIZE = 54;
    public static final int SAVE_SLOT = 45;
    public static final int RESET_SLOT = 49;
    public static final int CANCEL_SLOT = 53;
    public static final int BACK_SLOT = 45;

    private KitEditorMenu() {
    }

    public static Inventory launcher(KitService service) {
        LauncherHolder holder = new LauncherHolder();
        Inventory inventory = Bukkit.createInventory(holder, LAUNCHER_SIZE, Component.text("Siege Kit"));
        holder.attach(inventory);
        inventory.setItem(LAUNCHER_EQUIP_SLOT, labelled(
                new ItemStack(Material.NETHERITE_SWORD),
                Component.text("Equip My Siege Kit", NamedTextColor.GOLD),
                List.of(Component.text("Uses your saved kit, or the server default", NamedTextColor.GRAY))
        ));
        Material customizeIcon = service.hasEditableChoices() ? Material.ANVIL : Material.BARRIER;
        Component customizeLore = service.hasEditableChoices()
                ? Component.text("Make it your own on every map", NamedTextColor.GRAY)
                : Component.text("No replacement choices are configured", NamedTextColor.RED);
        inventory.setItem(LAUNCHER_CUSTOMIZE_SLOT, labelled(
                new ItemStack(customizeIcon),
                Component.text("Customize My Siege Kit", service.hasEditableChoices()
                        ? NamedTextColor.GOLD
                        : NamedTextColor.RED),
                List.of(customizeLore)
        ));
        return inventory;
    }

    public static Inventory editor(KitService service, KitSelection selection, long generation) {
        Map<Integer, Integer> guiToKitSlot = new LinkedHashMap<>();
        EditorHolder holder = new EditorHolder(generation, guiToKitSlot);
        Inventory inventory = Bukkit.createInventory(holder, EDITOR_SIZE, Component.text("Customize Siege Kit"));
        holder.attach(inventory);

        int guiSlot = 0;
        for (KitChoiceCatalog.ChoiceGroup group : service.editableGroups()) {
            guiToKitSlot.put(guiSlot, group.slot());
            KitChoiceCatalog.Choice selected = group.choice(selection.choiceAt(group.slot()))
                    .orElseGet(() -> group.choice(KitChoiceCatalog.DEFAULT_CHOICE).orElseThrow());
            // The editor is a visual draft of the effective kit. On a first
            // open that means the administrator's default slot; after a saved
            // choice it means the player's replacement, rather than a generic
            // category icon that hides what will actually be equipped.
            KitItemSpec selectedSpec = selected.resolve(service.snapshot(), group.slot());
            ItemStack display = selectedSpec == null
                    ? new ItemStack(Material.BARRIER)
                    : KitItems.create(selectedSpec);
            if (display == null) {
                display = new ItemStack(Material.BARRIER);
            }
            inventory.setItem(guiSlot, labelled(
                    display,
                    Component.text(group.displayName(), NamedTextColor.GOLD),
                    List.of(
                            Component.text("Selected: " + choiceName(service, group, selected), NamedTextColor.GREEN),
                            Component.text("Click to choose a replacement", NamedTextColor.GRAY)
                    )
            ));
            guiSlot++;
        }

        inventory.setItem(SAVE_SLOT, labelled(
                new ItemStack(Material.LIME_DYE),
                Component.text("Save & Equip", NamedTextColor.GREEN),
                List.of(Component.text("Save globally, close, and receive this kit", NamedTextColor.GRAY))
        ));
        inventory.setItem(RESET_SLOT, labelled(
                new ItemStack(Material.CHEST),
                Component.text("Reset Draft to Default", NamedTextColor.YELLOW),
                List.of(Component.text("Nothing changes until you Save & Equip", NamedTextColor.GRAY))
        ));
        inventory.setItem(CANCEL_SLOT, labelled(
                new ItemStack(Material.BARRIER),
                Component.text("Cancel Without Saving", NamedTextColor.RED),
                List.of(Component.text("Discard this draft", NamedTextColor.GRAY))
        ));
        return inventory;
    }

    public static Inventory choices(
            KitService service,
            KitChoiceCatalog.ChoiceGroup group,
            KitSelection selection,
            long generation
    ) {
        Map<Integer, String> guiToChoice = new LinkedHashMap<>();
        ChoiceHolder holder = new ChoiceHolder(generation, group.slot(), guiToChoice);
        Inventory inventory = Bukkit.createInventory(
                holder,
                EDITOR_SIZE,
                Component.text("Choose: " + group.displayName())
        );
        holder.attach(inventory);

        int guiSlot = 0;
        String selectedKey = selection.choiceAt(group.slot());
        for (KitChoiceCatalog.Choice choice : group.choices()) {
            KitItemSpec spec = choice.resolve(service.snapshot(), group.slot());
            ItemStack icon = spec == null ? new ItemStack(Material.BARRIER) : KitItems.create(spec);
            if (icon == null) {
                continue;
            }
            guiToChoice.put(guiSlot, choice.key());
            boolean selected = selectedKey.equals(choice.key());
            inventory.setItem(guiSlot, labelled(
                    icon,
                    Component.text(
                            choiceName(service, group, choice) + (selected ? " (Selected)" : ""),
                            selected ? NamedTextColor.GREEN : NamedTextColor.GOLD
                    ),
                    List.of(Component.text(spec == null ? "Click to leave this slot empty" : "Click to use this replacement", NamedTextColor.GRAY))
            ));
            guiSlot++;
        }

        inventory.setItem(BACK_SLOT, labelled(
                new ItemStack(Material.ARROW),
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return without changing this slot", NamedTextColor.GRAY))
        ));
        inventory.setItem(CANCEL_SLOT, labelled(
                new ItemStack(Material.BARRIER),
                Component.text("Cancel Without Saving", NamedTextColor.RED),
                List.of(Component.text("Discard the entire draft", NamedTextColor.GRAY))
        ));
        return inventory;
    }

    public static Inventory saving(long generation) {
        SavingHolder holder = new SavingHolder(generation);
        Inventory inventory = Bukkit.createInventory(holder, LAUNCHER_SIZE, Component.text("Saving Siege Kit"));
        holder.attach(inventory);
        inventory.setItem(13, labelled(
                new ItemStack(Material.CLOCK),
                Component.text("Saving...", NamedTextColor.YELLOW),
                List.of(Component.text("Your kit will equip after the save succeeds", NamedTextColor.GRAY))
        ));
        return inventory;
    }

    private static ItemStack labelled(ItemStack stack, Component name, List<Component> lore) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private static String choiceName(
            KitService service,
            KitChoiceCatalog.ChoiceGroup group,
            KitChoiceCatalog.Choice choice
    ) {
        return choice.useDefault() && choice.resolve(service.snapshot(), group.slot()) == null
                ? "Leave Empty"
                : choice.displayName();
    }

    public static final class LauncherHolder extends BaseHolder {
    }

    public static final class EditorHolder extends SessionHolder {

        private final Map<Integer, Integer> guiToKitSlot;

        private EditorHolder(long generation, Map<Integer, Integer> guiToKitSlot) {
            super(generation);
            this.guiToKitSlot = guiToKitSlot;
        }

        public Integer kitSlotAt(int guiSlot) {
            return guiToKitSlot.get(guiSlot);
        }
    }

    public static final class ChoiceHolder extends SessionHolder {

        private final int kitSlot;
        private final Map<Integer, String> guiToChoice;

        private ChoiceHolder(long generation, int kitSlot, Map<Integer, String> guiToChoice) {
            super(generation);
            this.kitSlot = kitSlot;
            this.guiToChoice = guiToChoice;
        }

        public int kitSlot() {
            return kitSlot;
        }

        public String choiceAt(int guiSlot) {
            return guiToChoice.get(guiSlot);
        }
    }

    public static final class SavingHolder extends SessionHolder {

        private SavingHolder(long generation) {
            super(generation);
        }
    }

    public abstract static class SessionHolder extends BaseHolder {

        private final long generation;

        private SessionHolder(long generation) {
            this.generation = generation;
        }

        public long generation() {
            return generation;
        }
    }

    public abstract static class BaseHolder implements InventoryHolder {

        private Inventory inventory;

        final void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
