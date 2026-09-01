package woo.siegePlugin.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import woo.siegePlugin.combat.CombatTagStatus;
import woo.siegePlugin.map.BaseClaim;
import woo.siegePlugin.team.Team;

import java.util.Objects;

/** One final decision for claimed entrances and the global combat gate rule. */
public final class BaseClaimInteractionListener implements Listener {

    private final BaseClaimPolicy claims;
    private final CombatTagStatus combatTags;

    public BaseClaimInteractionListener(BaseClaimPolicy claims, CombatTagStatus combatTags) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.combatTags = Objects.requireNonNull(combatTags, "combatTags");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        boolean gate = isFenceGate(block.getType());
        BaseClaim claim = claims.claimAt(block).orElse(null);
        boolean entrance = isEntranceInteraction(event.getAction(), block);
        AccessDecision decision = decide(
                claim == null ? null : claim.team(), claims.fighterTeam(event.getPlayer()).orElse(null),
                entrance, gate && event.getAction() == Action.RIGHT_CLICK_BLOCK,
                combatTags.isInCombat(event.getPlayer())
        );
        if (decision == AccessDecision.NONE) {
            return;
        }
        if (decision == AccessDecision.BLOCK_COMBAT) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "You cannot use fence gates while in combat.", NamedTextColor.RED
            ));
            return;
        }
        if (decision == AccessDecision.DENY_CLAIM) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "Only " + claim.team().defaultDisplayName() + " can use this base entrance.",
                    NamedTextColor.RED
            ));
            return;
        }
        // Owning-team claim access intentionally overrides Towny and combat tagging.
        event.setCancelled(false);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.ALLOW);
    }

    static AccessDecision decide(
            Team claimOwner, Team fighterTeam, boolean entranceInteraction,
            boolean fenceGateInteraction, boolean combatTagged
    ) {
        if (claimOwner != null) {
            if (!entranceInteraction) return AccessDecision.NONE;
            return fighterTeam == claimOwner ? AccessDecision.ALLOW_CLAIM : AccessDecision.DENY_CLAIM;
        }
        return fenceGateInteraction && combatTagged ? AccessDecision.BLOCK_COMBAT : AccessDecision.NONE;
    }

    enum AccessDecision {
        ALLOW_CLAIM,
        DENY_CLAIM,
        BLOCK_COMBAT,
        NONE
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityInteract(EntityInteractEvent event) {
        if (!(event.getEntity() instanceof Player) && claims.claimAt(event.getBlock()).isPresent()
                && isPhysicalControl(event.getBlock().getType())) {
            event.setCancelled(true);
        }
    }

    static boolean isEntranceInteraction(Action action, Block block) {
        if (action == Action.PHYSICAL) {
            return isPhysicalControl(block.getType());
        }
        return action == Action.RIGHT_CLICK_BLOCK && (block.getBlockData() instanceof Openable
                || block.getType() == Material.LEVER || isButton(block.getType()));
    }

    static boolean isFenceGate(Material material) {
        return material.name().endsWith("_FENCE_GATE");
    }

    private static boolean isButton(Material material) {
        return Tag.BUTTONS.isTagged(material);
    }

    private static boolean isPhysicalControl(Material material) {
        return Tag.PRESSURE_PLATES.isTagged(material);
    }
}
