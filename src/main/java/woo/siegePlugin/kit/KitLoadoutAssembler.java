package woo.siegePlugin.kit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rebuilds a complete kit from trusted defaults and stable configured choices. */
public final class KitLoadoutAssembler {

    private final KitSnapshot snapshot;
    private final KitChoiceCatalog catalog;

    public KitLoadoutAssembler(KitSnapshot snapshot, KitChoiceCatalog catalog) {
        this.snapshot = snapshot;
        this.catalog = catalog;
    }

    public Resolved resolve(KitSelection requested) {
        Map<Integer, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : requested.choices().entrySet()) {
            KitChoiceCatalog.ChoiceGroup group = catalog.compatibleGroupAt(entry.getKey(), snapshot).orElse(null);
            if (group == null || KitChoiceCatalog.DEFAULT_CHOICE.equals(entry.getValue())) {
                continue;
            }
            if (group.choice(entry.getValue()).filter(choice -> !choice.useDefault()).isPresent()) {
                normalized.put(entry.getKey(), entry.getValue());
            }
        }

        KitSelection selection = new KitSelection(normalized);
        Map<Integer, KitItemSpec> specs = new LinkedHashMap<>(snapshot.slots());
        for (Map.Entry<Integer, String> entry : selection.choices().entrySet()) {
            KitChoiceCatalog.ChoiceGroup group = catalog.compatibleGroupAt(entry.getKey(), snapshot).orElseThrow();
            KitChoiceCatalog.Choice choice = group.choice(entry.getValue()).orElseThrow();
            specs.put(entry.getKey(), choice.resolve(snapshot, entry.getKey()));
        }
        return new Resolved(selection, Map.copyOf(specs), !selection.equals(requested));
    }

    public List<String> findProblems(KitSelection selection) {
        return new KitValidator(snapshot, catalog).findProblems(selection);
    }

    public record Resolved(KitSelection selection, Map<Integer, KitItemSpec> specs, boolean healed) {

        public KitLoadout createLoadout() {
            return KitLoadout.fromSpecs(specs);
        }
    }
}
