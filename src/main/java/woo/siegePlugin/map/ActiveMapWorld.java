package woo.siegePlugin.map;

import org.bukkit.World;

import java.nio.file.Path;
import java.util.Objects;

/** A loaded, disposable runtime copy of one immutable map template. */
public record ActiveMapWorld(SiegeMap map, World world, Path folder) {
    public ActiveMapWorld {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(folder, "folder");
    }
}
