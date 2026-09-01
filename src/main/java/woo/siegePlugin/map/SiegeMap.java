package woo.siegePlugin.map;

import woo.siegePlugin.team.Team;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Complete SiegePlugin-specific contract for one immutable world template. */
public record SiegeMap(
        String id,
        String displayName,
        String templateFolder,
        MapPoint redSpawn,
        MapPoint blueSpawn,
        MapPoint capturePoint,
        int captureRadius,
        MapBounds bounds,
        Set<BaseClaim> baseClaims
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
        baseClaims = Set.copyOf(Objects.requireNonNull(baseClaims, "baseClaims"));
        Set<String> occupiedChunks = new LinkedHashSet<>();
        for (BaseClaim claim : baseClaims) {
            String chunk = claim.chunkX() + ":" + claim.chunkZ();
            if (!occupiedChunks.add(chunk)) {
                throw new IllegalArgumentException("base chunk " + chunk + " is assigned to both teams");
            }
        }
    }

    /** Compatibility constructor for maps created before base claims existed. */
    public SiegeMap(
            String id, String displayName, String templateFolder,
            MapPoint redSpawn, MapPoint blueSpawn, MapPoint capturePoint,
            int captureRadius, MapBounds bounds
    ) {
        this(id, displayName, templateFolder, redSpawn, blueSpawn, capturePoint,
                captureRadius, bounds, Set.of());
    }

    public Set<BaseClaim> claimsFor(Team team) {
        return baseClaims.stream().filter(claim -> claim.team() == team)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Optional<BaseClaim> claimAt(int blockX, int blockZ) {
        return baseClaims.stream().filter(claim -> claim.containsBlock(blockX, blockZ)).findFirst();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
