package woo.siegePlugin.title;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Locale;
import java.util.Optional;

/** The server-wide ranks rendered in the native Tab list, never as nametags. */
public enum PlayerTitle {
    OWNER("Owner", TextColor.color(255, 255, 170)),
    ADMIN("Admin", TextColor.color(255, 85, 85)),
    MODERATOR("Moderator", TextColor.color(210, 170, 255)),
    HELPER("Helper", TextColor.color(130, 210, 255)),
    MEMBER("Member", TextColor.color(170, 170, 170));

    private final String label;
    private final TextColor color;

    PlayerTitle(String label, TextColor color) {
        this.label = label;
        this.color = color;
    }

    public String storageValue() {
        return name();
    }

    public static Optional<PlayerTitle> fromStorage(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public Component prefix() {
        return Component.text(label + " ", color, TextDecoration.BOLD);
    }

    public Component playerListName(String playerName) {
        return prefix().append(Component.text(playerName));
    }
}
