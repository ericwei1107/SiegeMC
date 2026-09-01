package woo.siegePlugin.mobility;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Independent active-siege cooldowns for pearls and riptide launches. */
public final class MobilityCooldownListener implements Listener {
    private final Predicate<Player> eligibleFighter;
    private final MobilityCooldownSettings settings;
    private final Clock clock;
    private final Map<UUID, Instant> pearlReadyAt = new HashMap<>();
    private final Map<UUID, Instant> riptideReadyAt = new HashMap<>();
    public MobilityCooldownListener(Predicate<Player> eligibleFighter, MobilityCooldownSettings settings) { this(eligibleFighter, settings, Clock.systemUTC()); }
    MobilityCooldownListener(Predicate<Player> eligibleFighter, MobilityCooldownSettings settings, Clock clock) { this.eligibleFighter = eligibleFighter; this.settings = settings; this.clock = clock; }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player player) || !eligibleFighter.test(player)) return;
        Instant now = clock.instant();
        if (isCoolingDown(pearlReadyAt, player.getUniqueId(), now)) { event.setCancelled(true); player.sendActionBar(Component.text("Ender pearls are on cooldown.")); return; }
        pearlReadyAt.put(player.getUniqueId(), now.plus(settings.pearlCooldown()));
        player.setCooldown(Material.ENDER_PEARL, Math.toIntExact(settings.pearlCooldown().toSeconds() * 20L));
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        if (!eligibleFighter.test(player)) return;
        Instant now = clock.instant();
        if (isCoolingDown(riptideReadyAt, player.getUniqueId(), now)) { event.setCancelled(true); player.sendActionBar(Component.text("Riptide is on cooldown.")); return; }
        riptideReadyAt.put(player.getUniqueId(), now.plus(settings.riptideCooldown()));
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { pearlReadyAt.remove(event.getPlayer().getUniqueId()); riptideReadyAt.remove(event.getPlayer().getUniqueId()); }
    public void clearAll() { pearlReadyAt.clear(); riptideReadyAt.clear(); }
    static boolean isCoolingDown(Map<UUID, Instant> readyAt, UUID playerId, Instant now) { Instant ready = readyAt.get(playerId); return ready != null && ready.isAfter(now); }
}
