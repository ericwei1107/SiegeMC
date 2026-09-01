package woo.siegePlugin.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

/** Private, transition-only notifications for a fighter's own claimed area. */
public final class BaseClaimBoundaryListener implements Listener {

    private static final Component ENTERED = Component.text("You have entered your claim", NamedTextColor.GREEN);
    private static final Component EXITED = Component.text("You have exited your claim", NamedTextColor.RED);
    private final BaseClaimPolicy claims;

    public BaseClaimBoundaryListener(BaseClaimPolicy claims) {
        this.claims = Objects.requireNonNull(claims, "claims");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        notifyBoundary(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        notifyBoundary(event.getPlayer(), event.getFrom(), event.getTo());
    }

    private void notifyBoundary(org.bukkit.entity.Player player, Location from, Location to) {
        if (to == null || sameHorizontalBlock(from, to)) {
            return;
        }
        boolean before = claims.isOwnClaim(player, from);
        boolean after = claims.isOwnClaim(player, to);
        Component message = transitionMessage(before, after);
        if (message != null) player.sendMessage(message);
    }

    static boolean sameHorizontalBlock(Location from, Location to) {
        return Objects.equals(from.getWorld(), to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ();
    }

    static Component transitionMessage(boolean before, boolean after) {
        if (before == after) return null;
        return after ? ENTERED : EXITED;
    }
}
