package woo.siegePlugin.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/** Temporary first-join tutorial copy; replace its sample pages when final copy is available. */
public final class TutorialBook {

    private TutorialBook() {
    }

    public static ItemStack create() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Component.text("SiegeMC Tutorial", NamedTextColor.GOLD));
        meta.author(Component.text("SiegeMC", NamedTextColor.GRAY));
        meta.addPages(
                Component.text("Welcome to SiegeMC!\n\nThis is a temporary tutorial. More detail is coming soon."),
                Component.text("Joining a siege\n\nUse the Join Siege compass in the lobby, or run /siege join."),
                Component.text("The battle\n\nWork with your team, control the banner, and watch your surroundings."),
                Component.text("Combat rules\n\nWhile tagged in combat, commands and escape actions are restricted.")
        );
        book.setItemMeta(meta);
        return book;
    }
}
