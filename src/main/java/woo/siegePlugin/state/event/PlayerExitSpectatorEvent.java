package woo.siegePlugin.state.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class PlayerExitSpectatorEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerExitSpectatorEvent(Player player) {
        super(player);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
