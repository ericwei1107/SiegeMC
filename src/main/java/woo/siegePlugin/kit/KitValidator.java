package woo.siegePlugin.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates player selections against the current snapshot and configured choice catalog. */
public final class KitValidator {

    private final KitSnapshot snapshot;
    private final KitChoiceCatalog catalog;

    public KitValidator(KitSnapshot snapshot, KitChoiceCatalog catalog) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public List<String> findProblems(KitSelection selection) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : selection.choices().entrySet()) {
            int slot = entry.getKey();
            String choiceKey = entry.getValue();
            KitChoiceCatalog.ChoiceGroup group = catalog.compatibleGroupAt(slot, snapshot).orElse(null);
            if (group == null) {
                problems.add("slot " + slot + " is not an editable kit slot");
                continue;
            }
            KitChoiceCatalog.Choice choice = group.choice(choiceKey).orElse(null);
            if (choice == null) {
                problems.add("slot " + slot + " references unknown choice " + choiceKey);
            } else if (choice.useDefault()) {
                problems.add("default selections must be stored by omitting slot " + slot);
            }
        }
        return List.copyOf(problems);
    }

    public boolean isValid(KitSelection selection) {
        return findProblems(selection).isEmpty();
    }

    /** Defense-in-depth check that the assembled inventory exactly matches trusted reconstruction. */
    public List<String> findLoadoutProblems(KitSelection selection, KitLoadout loadout) {
        return findSpecProblems(selection, loadout.describe());
    }

    /** Bukkit-free defense-in-depth check used before any ItemStack is constructed. */
    public List<String> findSpecProblems(KitSelection selection, Map<Integer, KitItemSpec> assembled) {
        List<String> problems = new ArrayList<>(findProblems(selection));
        if (!problems.isEmpty()) {
            return List.copyOf(problems);
        }
        Map<Integer, KitItemSpec> expected = new KitLoadoutAssembler(snapshot, catalog).resolve(selection).specs();
        if (!expected.equals(assembled)) {
            problems.add("assembled kit contents do not match the configured selection");
        }
        return List.copyOf(problems);
    }
}
