package woo.siegePlugin.team;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import woo.siegePlugin.round.ActiveCombatEligibility;

import java.util.Locale;
import java.util.Objects;

/** Routes Towny's spawn command to the current clean-copy round spawn. */
public final class TownSpawnCommandListener implements Listener {

    private final ActiveCombatEligibility eligibility;
    private final TeamSpawnLocations spawns;

    public TownSpawnCommandListener(ActiveCombatEligibility eligibility, TeamSpawnLocations spawns) {
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.spawns = Objects.requireNonNull(spawns, "spawns");
    }

    // HIGH lets CombatTaggedCommandListener reject tagged players first at LOWEST,
    // while still cancelling Towny's old configured-home teleport before Towny runs.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isTownSpawnCommand(event.getMessage())) {
            return;
        }
        eligibility.fighterTeam(event.getPlayer()).ifPresent(team -> {
            event.setCancelled(true);
            if (!event.getPlayer().teleport(spawns.get(team))) {
                event.getPlayer().sendMessage("Could not teleport you to your team spawn. Contact an administrator.");
            }
        });
    }

    static boolean isTownSpawnCommand(String message) {
        String[] parts = message.trim().split("\\s+");
        if (parts.length != 2 || !parts[1].equalsIgnoreCase("spawn")) {
            return false;
        }
        String label = parts[0].startsWith("/") ? parts[0].substring(1) : parts[0];
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "t", "town", "towny:t", "towny:town" -> true;
            default -> false;
        };
    }
}
