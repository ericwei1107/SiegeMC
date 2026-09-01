package woo.siegePlugin.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.Objects;

/** Makes template terrain immutable inside every active team base claim. */
public final class BaseTerrainProtectionListener implements Listener {

    private static final Component DENIED = Component.text(
            "Base terrain cannot be changed during a siege.", NamedTextColor.RED
    );
    private final BaseClaimPolicy claims;

    public BaseTerrainProtectionListener(BaseClaimPolicy claims) {
        this.claims = Objects.requireNonNull(claims, "claims");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        if (protects(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(DENIED);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        if (protects(event.getBlockPlaced())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(DENIED);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (protects(event.getBlockClicked().getRelative(event.getBlockFace()))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(DENIED);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (protects(event.getBlockClicked())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(DENIED);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFluid(BlockFromToEvent event) {
        if (protects(event.getBlock()) || protects(event.getToBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBurn(BlockBurnEvent event) {
        if (protects(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onIgnite(BlockIgniteEvent event) {
        if (protects(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSpread(BlockSpreadEvent event) {
        if (protects(event.getBlock()) || protects(event.getSource())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onGrow(BlockGrowEvent event) {
        if (protects(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFade(BlockFadeEvent event) {
        if (protects(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onForm(BlockFormEvent event) {
        if (protects(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (protects(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        if (protects(event.getBlock()) || event.getBlocks().stream().anyMatch(state -> protects(state.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (protects(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protects);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protects);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (protects(event.getBlock()) || event.getBlocks().stream().anyMatch(block ->
                protects(block) || protects(block.getRelative(event.getDirection())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (protects(event.getBlock()) || event.getBlocks().stream().anyMatch(block ->
                protects(block) || protects(block.getRelative(event.getDirection().getOppositeFace())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onStructureGrow(StructureGrowEvent event) {
        if (event.getBlocks().stream().anyMatch(state -> protects(state.getBlock()))) event.setCancelled(true);
    }

    private boolean protects(Block block) {
        return claims.claimAt(block).isPresent();
    }
}
