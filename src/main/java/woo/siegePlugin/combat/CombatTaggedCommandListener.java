package woo.siegePlugin.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import woo.siegePlugin.sound.SoundEffectService;

import java.util.Objects;

/** Blocks every player-issued command while CombatTag reports an active tag. */
public final class CombatTaggedCommandListener implements Listener {

    private final CombatTagStatus combatTags;
    private final SoundEffectService sounds;

    public CombatTaggedCommandListener(CombatTagStatus combatTags, SoundEffectService sounds) {
        this.combatTags = Objects.requireNonNull(combatTags, "combatTags");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!blocksCommand(combatTags.isInCombat(player))) {
            return;
        }
        event.setCancelled(true);
        sounds.playDenied(player);
        player.sendMessage("§cYou cannot use commands while in combat.");
    }

    static boolean blocksCommand(boolean combatTagged) {
        return combatTagged;
    }
}
