package woo.siegePlugin.arena;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Upper bound for synchronous structure capture/restore work on the server thread. */
public record ArenaSnapshotLimits(long maxBlocks) {

    static final String MAX_BLOCKS_PATH = "arena-reset.max-snapshot-blocks";
    private static final long DEFAULT_MAX_BLOCKS = 1_000_000L;

    public ArenaSnapshotLimits {
        if (maxBlocks <= 0L) {
            throw new IllegalArgumentException("Arena snapshot block limit must be positive");
        }
    }

    public static ArenaSnapshotLimits fromConfig(FileConfiguration config) {
        return new ArenaSnapshotLimits(config.getLong(MAX_BLOCKS_PATH, DEFAULT_MAX_BLOCKS));
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        if (config.isSet(MAX_BLOCKS_PATH) && config.getLong(MAX_BLOCKS_PATH, 0L) <= 0L) {
            problems.add(MAX_BLOCKS_PATH + " must be a positive number of blocks");
        }
        return problems;
    }

    public boolean permits(ArenaRegion region) {
        return region.blockCount() <= maxBlocks;
    }
}
