package woo.siegePlugin.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Compact, template-owned index of claimed double-chest pairs. */
final class TemplateSupplyIndex {

    record Entry(UUID id, int firstX, int firstY, int firstZ, int secondX, int secondY, int secondZ) {
    }

    private TemplateSupplyIndex() {
    }

    static String encode(Iterable<Entry> entries) {
        List<String> values = new ArrayList<>();
        for (Entry entry : entries) {
            values.add(entry.id() + "," + entry.firstX() + "," + entry.firstY() + "," + entry.firstZ()
                    + "," + entry.secondX() + "," + entry.secondY() + "," + entry.secondZ());
        }
        return String.join(";", values);
    }

    static Map<UUID, Entry> decode(String raw) {
        Map<UUID, Entry> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String value : raw.split(";")) {
            String[] fields = value.split(",", -1);
            if (fields.length != 7) {
                continue;
            }
            try {
                UUID id = UUID.fromString(fields[0]);
                result.put(id, new Entry(id, Integer.parseInt(fields[1]), Integer.parseInt(fields[2]),
                        Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Integer.parseInt(fields[5]),
                        Integer.parseInt(fields[6])));
            } catch (IllegalArgumentException ignored) {
                // A damaged template entry must not prevent its map from loading.
            }
        }
        return result;
    }
}
