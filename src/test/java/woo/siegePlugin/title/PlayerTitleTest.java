package woo.siegePlugin.title;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerTitleTest {
    @Test void memberIsTheStableDefaultTabPrefix() {
        assertEquals("Member Alex", PlainTextComponentSerializer.plainText().serialize(PlayerTitle.MEMBER.playerListName("Alex")));
        assertEquals(TextDecoration.State.TRUE, PlayerTitle.MEMBER.prefix().decoration(TextDecoration.BOLD));
    }
}
