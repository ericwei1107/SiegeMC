package woo.siegePlugin.storage;

import org.bukkit.inventory.ItemStack;
import woo.siegePlugin.team.Team;

import java.util.Objects;
import java.util.UUID;

/** Persisted definition of an infinite, team-owned potion double chest. */
public record PotionStorage(UUID id, PotionStorageKey key, Team team, ItemStack potion) {

    public PotionStorage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(potion, "potion");
        potion = potion.clone();
        potion.setAmount(1);
    }

    @Override
    public ItemStack potion() {
        return potion.clone();
    }
}
