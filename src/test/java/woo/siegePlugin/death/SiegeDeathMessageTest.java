package woo.siegePlugin.death;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SiegeDeathMessageTest {

    @Test
    void formatsAPlayerKillWithTheSiegeTagAndReferenceColors() {
        Component message = SiegeDeathMessage.killedByPlayer(Team.BLUE, "H4pnotic", "Nimboler", 150);

        assertEquals(
                "[Siege] > Defender H4pnotic was killed by Nimboler > Battle Points +150",
                plainText(message)
        );
        assertEquals(NamedTextColor.GOLD, message.color());
        assertEquals(NamedTextColor.AQUA, message.children().getFirst().color());
    }

    @Test
    void formatsAnUnattributedDeathWithoutInventingAKiller() {
        Component message = SiegeDeathMessage.died(Team.RED, "Rider", 275);

        assertEquals("[Siege] > Attacker Rider died > Battle Points +275", plainText(message));
    }

    private static String plainText(Component message) {
        return PlainTextComponentSerializer.plainText().serialize(message);
    }
}
