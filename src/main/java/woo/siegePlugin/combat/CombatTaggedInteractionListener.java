package woo.siegePlugin.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import woo.siegePlugin.arena.BaseClaimPolicy;

import java.util.Objects;

/** Prevents tagged players from using fence gates that Towny would otherwise allow. */
public final class CombatTaggedInteractionListener implements Listener {

    private final CombatTagStatus combatTags;
    private final BaseClaimPolicy claims;

    public CombatTaggedInteractionListener(CombatTagStatus combatTags, BaseClaimPolicy claims) {
        this.combatTags = Objects.requireNonNull(combatTags, "combatTags");
        this.claims = Objects.requireNonNull(claims, "claims");
    }

    /** Runs after ordinary protection, extending rather than bypassing Towny's gate rules. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Material material = event.getClickedBlock() == null ? null : event.getClickedBlock().getType();
        boolean ownClaim = event.getClickedBlock() != null
                && claims.isOwnClaim(event.getPlayer(), event.getClickedBlock().getLocation());
        if (!blocksFenceGate(event.getAction(), material, combatTags.isInCombat(event.getPlayer()), ownClaim)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("You cannot use fence gates while in combat.", NamedTextColor.RED));
    }

    static boolean blocksFenceGate(Action action, Material material, boolean combatTagged, boolean ownClaim) {
        return combatTagged
                && !ownClaim
                && action == Action.RIGHT_CLICK_BLOCK
                && material != null
                && material.name().endsWith("_FENCE_GATE");
    }
}
