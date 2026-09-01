package woo.siegePlugin.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Objects;

/** Blocks every player-issued command while CombatTag reports an active tag. */
public final class CombatTaggedCommandListener implements Listener {

    private final CombatTagStatus combatTags;

    public CombatTaggedCommandListener(CombatTagStatus combatTags) {
        this.combatTags = Objects.requireNonNull(combatTags, "combatTags");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!blocksCommand(combatTags.isInCombat(player))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§cYou cannot use commands while in combat.");
    }

    static boolean blocksCommand(boolean combatTagged) {
        return combatTagged;
    }
}
