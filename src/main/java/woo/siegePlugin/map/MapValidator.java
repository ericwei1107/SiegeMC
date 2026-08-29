package woo.siegePlugin.map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Admission rules a template must satisfy before it can host a live round.
 *
 * <p>Admission runs in two stages because the two kinds of problem are found in
 * different places. {@link #staticProblems} reads the manifest and the template
 * folder, so an operator can repair a map without copying it. {@link
 * #loadedCopyProblems} needs an actual loaded copy: only then can spawn footing,
 * headroom, capture placement, and the two halves of a supply chest be
 * checked.</p>
 */
public final class MapValidator {

    private MapValidator() {
    }

    /**
     * @param templateReadable whether the template folder resolved inside the
     *                         template root and contains readable world data
     */
    public static List<String> staticProblems(SiegeMap map, boolean templateReadable) {
        List<String> problems = new ArrayList<>();
        if (!templateReadable) {
            problems.add("template folder '" + map.templateFolder()
                    + "' is missing, escapes maps/templates, or has no level.dat");
        }
        checkBoundsContainment(problems, map, "red-spawn", map.redSpawn());
        checkBoundsContainment(problems, map, "blue-spawn", map.blueSpawn());
        checkBoundsContainment(problems, map, "capture-point", map.capturePoint());
        if (samePosition(map.redSpawn(), map.blueSpawn())) {
            problems.add("red-spawn and blue-spawn are the same position");
        }
        checkCaptureZone(problems, map);
        return List.copyOf(problems);
    }

    /**
     * Checks everything that requires the generated copy to exist.
     *
     * <p>Heights are validated against the world's own limits rather than the
     * vanilla overworld constants, because a custom template may legitimately
     * use a different build range.</p>
     */
    public static List<String> loadedCopyProblems(ActiveMapWorld active) {
        World world = active.world();
        SiegeMap map = active.map();
        List<String> problems = new ArrayList<>();
        checkSpawn(problems, world, map, "red-spawn", map.redSpawn());
        checkSpawn(problems, world, map, "blue-spawn", map.blueSpawn());
        checkCapturePlacement(problems, world, map);
        return List.copyOf(problems);
    }

    /** A spawn must be inside the world, standing on solid safe ground, with room to stand. */
    private static void checkSpawn(
            List<String> problems, World world, SiegeMap map, String name, MapPoint point
    ) {
        if (!withinWorldHeight(world, point)) {
            problems.add(name + " y=" + point.y() + " is outside this world's build range ("
                    + world.getMinHeight() + " to " + world.getMaxHeight() + ")");
            return;
        }
        Block feet = blockAt(world, point);
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        if (!ground.getType().isSolid()) {
            problems.add(name + " has no solid footing (" + ground.getType() + " below)");
        }
        if (isHazardous(ground)) {
            problems.add(name + " stands on hazardous ground (" + ground.getType() + ")");
        }
        if (!feet.isPassable() || !head.isPassable()) {
            problems.add(name + " is obstructed (" + feet.getType() + " / " + head.getType() + ")");
        }
        if (isHazardous(feet) || isHazardous(head)) {
            problems.add(name + " is inside a hazard");
        }
    }

    /** The banner must sit in a reachable, supported position inside the arena. */
    private static void checkCapturePlacement(List<String> problems, World world, SiegeMap map) {
        MapPoint capture = map.capturePoint();
        if (!withinWorldHeight(world, capture)) {
            problems.add("capture-point y=" + capture.y() + " is outside this world's build range ("
                    + world.getMinHeight() + " to " + world.getMaxHeight() + ")");
            return;
        }
        Block banner = blockAt(world, capture);
        if (!banner.getRelative(0, -1, 0).getType().isSolid()) {
            problems.add("capture-point has no solid block to stand the banner on");
        }
        if (isHazardous(banner)) {
            problems.add("capture-point is inside a hazard (" + banner.getType() + ")");
        }
    }

    private static boolean withinWorldHeight(World world, MapPoint point) {
        // A spawn on the very top block has no headroom, hence the -1.
        return point.y() >= world.getMinHeight() && point.y() < world.getMaxHeight() - 1;
    }

    private static Block blockAt(World world, MapPoint point) {
        return new Location(world, point.x(), point.y(), point.z()).getBlock();
    }

    private static boolean isHazardous(Block block) {
        return switch (block.getType()) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, SWEET_BERRY_BUSH,
                 POWDER_SNOW, WITHER_ROSE, POINTED_DRIPSTONE -> true;
            default -> false;
        };
    }

    private static void checkBoundsContainment(
            List<String> problems, SiegeMap map, String name, MapPoint point
    ) {
        if (!contains(map.bounds(), point.x(), point.z())) {
            problems.add(name + " is outside bounds");
        }
    }

    /**
     * The capture radius applies horizontally and vertically, so a banner placed
     * near an edge would let players score from outside the arena.
     */
    private static void checkCaptureZone(List<String> problems, SiegeMap map) {
        MapBounds bounds = map.bounds();
        MapPoint capture = map.capturePoint();
        int radius = map.captureRadius();
        if (capture.x() - radius < bounds.minX() || capture.x() + radius > bounds.maxX()
                || capture.z() - radius < bounds.minZ() || capture.z() + radius > bounds.maxZ()) {
            problems.add("capture-point radius " + radius + " reaches outside bounds");
        }
    }

    /** Shared containment rule so bounds mean the same thing to every caller. */
    public static boolean contains(MapBounds bounds, double x, double z) {
        return x >= bounds.minX() && x <= bounds.maxX() && z >= bounds.minZ() && z <= bounds.maxZ();
    }

    private static boolean samePosition(MapPoint left, MapPoint right) {
        return left.x() == right.x() && left.y() == right.y() && left.z() == right.z();
    }
}
