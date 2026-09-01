package woo.siegePlugin.death;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Red visual-only death fireworks; their damage is cancelled before CombatTag can observe it. */
public final class DeathFireworkEffects implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey effectKey;
    public DeathFireworkEffects(JavaPlugin plugin) { this.plugin = plugin; this.effectKey = new NamespacedKey(plugin, "death_firework"); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Firework firework = event.getEntity().getWorld().spawn(event.getEntity().getLocation().add(0, 0.4, 0), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.RED).withFade(Color.RED).flicker(true).build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.getPersistentDataContainer().set(effectKey, PersistentDataType.BYTE, (byte) 1);
            firework.detonate();
        });
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void cancelDeathFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework && firework.getPersistentDataContainer().has(effectKey, PersistentDataType.BYTE)) event.setCancelled(true);
    }
}
