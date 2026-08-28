package woo.siegePlugin.map;

import java.util.Objects;

/** Complete SiegePlugin-specific contract for one immutable world template. */
public record SiegeMap(
        String id,
        String displayName,
        String templateFolder,
        MapPoint redSpawn,
        MapPoint blueSpawn,
        MapPoint capturePoint,
        int captureRadius,
        MapBounds bounds
) {
    public SiegeMap {
        id = requireText(id, "id");
        displayName = requireText(displayName, "displayName");
        templateFolder = requireText(templateFolder, "templateFolder");
        if (templateFolder.contains("..") || templateFolder.contains("/") || templateFolder.contains("\\")) {
            throw new IllegalArgumentException("templateFolder must be a single folder name");
        }
        redSpawn = Objects.requireNonNull(redSpawn, "redSpawn");
        blueSpawn = Objects.requireNonNull(blueSpawn, "blueSpawn");
        capturePoint = Objects.requireNonNull(capturePoint, "capturePoint");
        if (captureRadius <= 0) {
            throw new IllegalArgumentException("captureRadius must be positive");
        }
        bounds = Objects.requireNonNull(bounds, "bounds");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
