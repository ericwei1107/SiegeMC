package woo.siegePlugin.death;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import woo.siegePlugin.team.Team;

import java.util.Objects;

/** Formats the public Siege announcement shown for every score-qualifying death. */
final class SiegeDeathMessage {

    private static final NamedTextColor PREFIX_COLOR = NamedTextColor.GOLD;
    private static final NamedTextColor MESSAGE_COLOR = NamedTextColor.AQUA;

    private SiegeDeathMessage() {
    }

    static Component killedByPlayer(Team victimTeam, String victimName, String killerName, long points) {
        return messagePrefix(victimTeam, victimName)
                .append(Component.text(" was killed by " + requireName(killerName) + " > Battle Points +" + points,
                        MESSAGE_COLOR));
    }

    static Component died(Team victimTeam, String victimName, long points) {
        return messagePrefix(victimTeam, victimName)
                .append(Component.text(" died > Battle Points +" + points, MESSAGE_COLOR));
    }

    private static Component messagePrefix(Team victimTeam, String victimName) {
        return Component.text("[Siege]", PREFIX_COLOR)
                .append(Component.text(" > " + role(victimTeam) + " " + requireName(victimName), MESSAGE_COLOR));
    }

    private static String role(Team team) {
        return Objects.requireNonNull(team, "victimTeam") == Team.RED ? "Attacker" : "Defender";
    }

    private static String requireName(String playerName) {
        return Objects.requireNonNull(playerName, "playerName");
    }
}
