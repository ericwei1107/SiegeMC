package woo.siegePlugin.minecart;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.capture.CaptureBanner;
import woo.siegePlugin.capture.CaptureGeometry;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TownyAdapter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Applies the Stage 4.5 damage coefficient to tagged siege-shop carts. */
public final class MinecartDamageListener implements Listener {

    private final JavaPlugin plugin;
    private final SiegeMinecartMarker marker;
    private final TownyAdapter townyAdapter;
    private CaptureBanner banner;
    private int captureRadiusBlocks;
    private final MinecartDamagePolicy damagePolicy;
    private final Map<UUID, MinecartHeadcounts> explosionHeadcounts = new HashMap<>();

    public MinecartDamageListener(
            JavaPlugin plugin,
            SiegeMinecartMarker marker,
            TownyAdapter townyAdapter,
            CaptureBanner banner,
            int captureRadiusBlocks,
            MinecartDamageSettings damageSettings
    ) {
        this.plugin = plugin;
        this.marker = marker;
        this.townyAdapter = townyAdapter;
        this.banner = banner;
        this.captureRadiusBlocks = captureRadiusBlocks;
        this.damagePolicy = new MinecartDamagePolicy(damageSettings);
    }

    public void rebind(CaptureBanner banner, int captureRadiusBlocks) {
        this.banner = banner;
        this.captureRadiusBlocks = captureRadiusBlocks;
        explosionHeadcounts.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMinecartDamage(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        if (!(event.getDamager() instanceof ExplosiveMinecart minecart) || !marker.isMarked(minecart)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Team victimTeam = townyAdapter.getPlayerTeam(victim).orElse(null);
        if (victimTeam == null) {
            return;
        }

        MinecartHeadcounts headcounts = explosionHeadcounts.computeIfAbsent(
                minecart.getUniqueId(),
                minecartId -> snapshotHeadcounts(minecartId)
        );
        if (!damagePolicy.usesFullVanillaDamage(victimTeam, headcounts)) {
            event.setDamage(damagePolicy.scaledRawDamage(event.getDamage(), victimTeam, headcounts));
        }
    }

    private MinecartHeadcounts snapshotHeadcounts(UUID minecartId) {
        Map<Team, Integer> counts = new EnumMap<>(Team.class);
        counts.put(Team.RED, 0);
        counts.put(Team.BLUE, 0);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!countsAsNearbyFighter(
                    player.isOnline(),
                    player.isDead(),
                    player.getGameMode() == GameMode.SPECTATOR,
                    CaptureGeometry.isWithinCaptureZone(
                            player.getLocation(),
                            banner.location(),
                            captureRadiusBlocks
                    )
            )) {
                continue;
            }

            townyAdapter.getPlayerTeam(player).ifPresent(team -> counts.compute(team, (ignored, count) -> count + 1));
        }

        // Explosion damage events are synchronous. Retaining the snapshot
        // through the end of this tick gives every victim identical numbers.
        plugin.getServer().getScheduler().runTask(plugin, () -> explosionHeadcounts.remove(minecartId));
        return new MinecartHeadcounts(counts.get(Team.RED), counts.get(Team.BLUE));
    }

    static boolean countsAsNearbyFighter(boolean online, boolean dead, boolean spectator, boolean withinCaptureZone) {
        return online && !dead && !spectator && withinCaptureZone;
    }
}
