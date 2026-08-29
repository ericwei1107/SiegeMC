package woo.siegePlugin.round;

import woo.siegePlugin.map.ActiveMapWorld;
import woo.siegePlugin.map.SiegeMap;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Clean-copy world operations the coordinator depends on.
 *
 * <p>This is a port over the existing native loader, not a replacement for it —
 * the copy/load/unload/delete behaviour stays exactly where it is. The
 * indirection exists so lifecycle tests can make a copy fail, hang past the
 * preparation timeout, or refuse to unload while a player remains.</p>
 */
public interface WorldLifecycle {

    CompletableFuture<ActiveMapWorld> load(SiegeMap map);

    /** Reopens the exact generated copy recorded by durable state. */
    CompletableFuture<ActiveMapWorld> resume(SiegeMap map, String runtimeWorldName);

    /** Unloads without saving, then deletes only the generated folder. */
    CompletableFuture<Void> unload(ActiveMapWorld activeWorld);

    /** Unloads and deletes a copy known only by name, after a restart. */
    CompletableFuture<Void> discard(String runtimeWorldName, String folder);

    /** Static admission: template containment, level.dat, and manifest sanity. */
    List<String> staticProblems(SiegeMap map);

    /**
     * Admission checks that need the copy loaded — spawn footing and headroom,
     * capture placement, and both halves of every configured supply chest.
     */
    List<String> loadedCopyProblems(ActiveMapWorld world);

    /** Generated folders on disk that no durable record accounts for. */
    List<String> untrackedGeneratedWorlds();
}
