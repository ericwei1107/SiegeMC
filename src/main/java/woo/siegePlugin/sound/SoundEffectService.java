package woo.siegePlugin.sound;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Centralizes the vanilla sound cues played for command outcomes and arena/lobby events. */
public final class SoundEffectService {

    private final boolean enabled;

    public SoundEffectService(boolean enabled) {
        this.enabled = enabled;
    }

    public void playDenied(Player player) {
        play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    public void playFailed(Player player) {
        play(player, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f);
    }

    public void playSuccess(Player player) {
        play(player, Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
    }

    public void playConfirmation(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
    }

    public void playTeleport(Player player) {
        play(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    public void playWelcome(Player player) {
        play(player, Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.0f);
    }

    private void play(Player player, Sound sound, float volume, float pitch) {
        if (!enabled || player == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
