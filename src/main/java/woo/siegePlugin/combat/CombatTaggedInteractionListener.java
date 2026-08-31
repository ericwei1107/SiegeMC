package woo.siegePlugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Objects;

/** Prevents tagged players from using fence gates that Towny would otherwise allow. */
public final class CombatTaggedInteractionListener implements Listener {

    private final CombatTagStatus combatTags;

    public CombatTaggedInteractionListener(CombatTagStatus combatTags) {
        this.combatTags = Objects.requireNonNull(combatTags, "combatTags");
    }

    /** Runs after ordinary protection, extending rather than bypassing Towny's gate rules. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Material material = event.getClickedBlock() == null ? null : event.getClickedBlock().getType();
        if (!blocksFenceGate(event.getAction(), material, combatTags.isInCombat(event.getPlayer()))) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("You cannot use fence gates while in combat.", NamedTextColor.RED));
    }

    static boolean blocksFenceGate(Action action, Material material, boolean combatTagged) {
        return combatTagged
                && action == Action.RIGHT_CLICK_BLOCK
                && material != null
                && material.name().endsWith("_FENCE_GATE");
    }
}
