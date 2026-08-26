package woo.siegePlugin.kit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checks a loadout against the approved profile.
 *
 * <p>Runs on save as defence in depth, and on load — where it is the real
 * guard, since stored rows can outlive a profile change or be tampered
 * with.</p>
 */
public final class KitValidator {

    private final KitProfile profile;

    public KitValidator(KitProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    /** Returns every problem found; an empty list means the loadout is legal. */
    public List<String> findProblems(Map<Integer, KitItemSpec> loadout) {
        List<String> problems = new ArrayList<>();
        Map<String, Integer> totals = new HashMap<>();

        for (Map.Entry<Integer, KitItemSpec> entry : loadout.entrySet()) {
            int slot = entry.getKey();
            KitItemSpec item = entry.getValue();

            if (slot < 0 || slot >= KitSlotKind.TOTAL_SLOTS) {
                problems.add("slot " + slot + " is outside the inventory");
                continue;
            }

            KitAllowance allowance = profile.allowanceFor(item).orElse(null);
            if (allowance == null) {
                problems.add(item.material() + " is not part of the base kit");
                continue;
            }

            if (!allowance.placement().accepts(slot)) {
                problems.add(item.material() + " cannot go in slot " + slot
                        + " (expected " + allowance.placement() + ")");
            }
            if (item.amount() <= 0) {
                problems.add(item.material() + " has a non-positive amount");
            } else if (item.amount() > allowance.maxPerSlot()) {
                problems.add(item.material() + " exceeds " + allowance.maxPerSlot() + " per slot");
            }
            if (!item.enchantments().equals(allowance.enchantments())) {
                problems.add(item.material() + " has enchantments that do not match the base kit");
            }
            if (!Objects.equals(item.potionType(), allowance.potionType())) {
                problems.add(item.material() + " is not the approved potion form");
            }

            if (allowance != null) {
                totals.merge(allowance.key(), Math.max(0, item.amount()), Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> total : totals.entrySet()) {
            profile.allowanceForKey(total.getKey()).ifPresent(allowance -> {
                if (total.getValue() > allowance.maxTotal()) {
                    problems.add(total.getKey() + " exceeds the total limit of " + allowance.maxTotal());
                }
            });
        }

        return problems;
    }

    public boolean isValid(Map<Integer, KitItemSpec> loadout) {
        return findProblems(loadout).isEmpty();
    }

    /**
     * How many more units of a material a loadout may still take, given what it
     * already holds.
     */
    public int remainingAllowance(Map<Integer, KitItemSpec> loadout, String allowanceKey) {
        KitAllowance allowance = profile.allowanceForKey(allowanceKey).orElse(null);
        if (allowance == null) {
            return 0;
        }

        int used = loadout.values().stream()
                .filter(item -> profile.allowanceFor(item)
                        .map(candidate -> candidate.key().equals(allowanceKey))
                        .orElse(false))
                .mapToInt(KitItemSpec::amount)
                .sum();
        return Math.max(0, allowance.maxTotal() - used);
    }
}
