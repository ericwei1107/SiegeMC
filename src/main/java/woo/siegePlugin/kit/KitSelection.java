package woo.siegePlugin.kit;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable configured choice ids selected for a player's editable storage slots. */
public record KitSelection(Map<Integer, String> choices) {

    public KitSelection {
        choices = Map.copyOf(new LinkedHashMap<>(choices));
    }

    public static KitSelection empty() {
        return new KitSelection(Map.of());
    }

    public KitSelection withChoice(int slot, String choiceKey) {
        Map<Integer, String> updated = new LinkedHashMap<>(choices);
        if (KitChoiceCatalog.DEFAULT_CHOICE.equals(choiceKey)) {
            updated.remove(slot);
        } else {
            updated.put(slot, choiceKey);
        }
        return new KitSelection(updated);
    }

    public String choiceAt(int slot) {
        return choices.getOrDefault(slot, KitChoiceCatalog.DEFAULT_CHOICE);
    }
}
