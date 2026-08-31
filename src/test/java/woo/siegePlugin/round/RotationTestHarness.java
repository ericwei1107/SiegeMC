package woo.siegePlugin.round;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import woo.siegePlugin.map.ActiveMapWorld;
import woo.siegePlugin.map.MapManifest;
import woo.siegePlugin.map.SiegeMap;
import woo.siegePlugin.persistence.MatchDefinition;
import woo.siegePlugin.persistence.MatchScoreDao;
import woo.siegePlugin.persistence.MatchStatsDao;
import woo.siegePlugin.persistence.RotationStateDao;
import woo.siegePlugin.persistence.SiegeDatabase;
import woo.siegePlugin.persistence.WorldCleanupDao;
import woo.siegePlugin.stats.MatchStatsTracker;
import woo.siegePlugin.team.Team;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deterministic in-memory rig for the rotation lifecycle.
 *
 * <p>Persistence is a real temporary SQLite database, because the compare-and-set
 * and transaction rules being tested live in SQL. Everything Bukkit-shaped —
 * time, the server thread, players, and world loading — is faked so a test can
 * drive forty seconds of intermission, fail one player's teleport, or hang a map
 * copy, all without a server.</p>
 */
final class RotationTestHarness implements AutoCloseable {

    final SiegeDatabase database;
    final RotationStateDao stateDao;
    final MatchScoreDao scoreDao;
    final MatchStatsDao statsDao;
    final WorldCleanupDao cleanupDao;
    final ActiveRoundProvider rounds = new ActiveRoundProvider();
    final RoundRoster roster = new RoundRoster();
    final MatchStatsTracker stats = new MatchStatsTracker();
    final FakeScheduler scheduler = new FakeScheduler();
    final FakeAudience audience = new FakeAudience();
    final FakeWorlds worlds = new FakeWorlds();
    final List<ActiveRoundContext> published = new ArrayList<>();
    final List<String> scoringFailures = new ArrayList<>();

    /** Set non-null to make the next activateMatch fail. */
    Throwable scoringFailure;

    private final RotationCoordinator coordinator;

    RotationTestHarness(Path databasePath, List<String> enabledMaps) {
        this.database = new SiegeDatabase(databasePath);
        this.stateDao = new RotationStateDao(database);
        this.scoreDao = new MatchScoreDao(database);
        this.statsDao = new MatchStatsDao(database);
        this.cleanupDao = new WorldCleanupDao(database);
        MapManifest manifest = manifestOf(enabledMaps);
        this.coordinator = new RotationCoordinator(
                quietLogger(),
                scheduler,
                audience,
                worlds,
                rounds,
                roster,
                stateDao,
                scoreDao,
                statsDao,
                cleanupDao,
                () -> manifest,
                (mapId, bounds) -> List.of(),
                this::activateMatch,
                stats,
                new RotationSettings(Duration.ofSeconds(40), Duration.ofSeconds(300), Duration.ofMinutes(5)),
                100L,
                published::add,
                new Random(42L)
        );
    }

    RotationCoordinator coordinator() {
        return coordinator;
    }

    /**
     * Starts the coordinator with map preparation held, queues the given
     * players, then releases the copy. This reproduces the real ordering, where
     * a copy takes seconds and players are queued well before it finishes.
     */
    void startAndQueue(UUID... players) {
        startHoldingPreparation(players);
        releasePreparation();
    }

    /** Starts and queues, but leaves the map copy in flight. */
    void startHoldingPreparation(UUID... players) {
        worlds.holdAllLoads = true;
        coordinator.start();
        settle();
        for (UUID player : players) {
            coordinator.requestJoin(player);
        }
        settle();
    }

    void releasePreparation() {
        worlds.holdAllLoads = false;
        worlds.releaseNextLoad("siege-active-");
        settle();
    }

    private void activateMatch(MatchDefinition definition, java.util.function.Consumer<Throwable> completion) {
        if (scoringFailure != null) {
            Throwable failure = scoringFailure;
            scoringFailure = null;
            scoringFailures.add(definition.matchId());
            completion.accept(failure);
            return;
        }
        scoreDao.loadOrCreate(definition).whenComplete((loaded, failure) -> scheduler.onServerThread(() ->
                completion.accept(failure == null
                        ? loaded.mismatchAgainst(definition).map(IllegalStateException::new).orElse(null)
                        : failure)
        ));
    }

    /**
     * Runs queued server-thread work until the lifecycle stops producing more.
     *
     * <p>Database futures complete on SQLite's own worker and only then queue
     * their continuation, so an empty queue does not mean the round has settled.
     * This waits for a sustained quiet period instead of stopping at the first
     * empty poll.</p>
     */
    void settle() {
        int consecutiveIdlePolls = 0;
        for (int guard = 0; guard < 5_000 && consecutiveIdlePolls < 25; guard++) {
            if (scheduler.drainOnce()) {
                consecutiveIdlePolls = 0;
            } else {
                consecutiveIdlePolls++;
                quietSleep();
            }
        }
    }

    void tick() {
        scheduler.runTick();
        settle();
    }

    void advance(Duration amount) {
        scheduler.advance(amount);
    }

    List<String> broadcasts() {
        return List.copyOf(audience.broadcasts);
    }

    boolean broadcastContains(String fragment) {
        return audience.broadcasts.stream().anyMatch(line -> line.contains(fragment));
    }

    RotationState durableState() throws Exception {
        return stateDao.load().get(5, TimeUnit.SECONDS).orElseThrow();
    }

    @Override
    public void close() {
        coordinator.stop();
        database.close();
    }

    private static void quietSleep() {
        try {
            Thread.sleep(2L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger("RotationTestHarness-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.OFF);
        return logger;
    }

    static MapManifest manifestOf(List<String> ids) {
        YamlConfiguration config = new YamlConfiguration();
        for (String id : ids) {
            String path = "maps." + id;
            config.set(path + ".enabled", true);
            config.set(path + ".display-name", "Siege of " + id);
            config.set(path + ".template-folder", id);
            config.set(path + ".red-spawn.x", -10.0D);
            config.set(path + ".red-spawn.y", 70.0D);
            config.set(path + ".red-spawn.z", -10.0D);
            config.set(path + ".blue-spawn.x", 10.0D);
            config.set(path + ".blue-spawn.y", 70.0D);
            config.set(path + ".blue-spawn.z", 10.0D);
            config.set(path + ".capture-point.x", 0.0D);
            config.set(path + ".capture-point.y", 70.0D);
            config.set(path + ".capture-point.z", 0.0D);
            config.set(path + ".capture-point.radius", 16);
            config.set(path + ".bounds.min-x", -256);
            config.set(path + ".bounds.min-z", -256);
            config.set(path + ".bounds.max-x", 256);
            config.set(path + ".bounds.max-z", 256);
        }
        return MapManifest.fromConfig(config);
    }

    /** A {@link World} that answers only the handful of calls rotation makes. */
    static World world(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "World[" + name + "]";
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }

    // ------------------------------------------------------------------ ports

    /** Runs "server thread" work only when a test asks, and controls the clock. */
    static final class FakeScheduler implements RoundScheduler {

        private final Deque<Runnable> queue = new ArrayDeque<>();
        private Runnable tick;
        // Real wall-clock time, not a fixed literal: WorldCleanupDao stamps
        // next_retry_at with actual System.currentTimeMillis(), so a frozen
        // "now" here would eventually drift behind it and make due() queries
        // fail once real time passes whatever date was hardcoded.
        private Instant now = Instant.now();

        @Override
        public synchronized void onServerThread(Runnable action) {
            queue.add(action);
        }

        @Override
        public void startTicking(Runnable tick) {
            this.tick = tick;
        }

        @Override
        public void stopTicking() {
            this.tick = null;
        }

        @Override
        public synchronized Instant now() {
            return now;
        }

        synchronized void advance(Duration amount) {
            now = now.plus(amount);
        }

        boolean isTicking() {
            return tick != null;
        }

        void runTick() {
            Runnable current = tick;
            if (current != null) {
                current.run();
            }
        }

        /** @return true when at least one action ran */
        boolean drainOnce() {
            List<Runnable> batch;
            synchronized (this) {
                if (queue.isEmpty()) {
                    return false;
                }
                batch = new ArrayList<>(queue);
                queue.clear();
            }
            batch.forEach(Runnable::run);
            return true;
        }
    }

    /** Records everything said and every movement attempt. */
    static final class FakeAudience implements RoundAudience {

        final List<String> broadcasts = new ArrayList<>();
        final Map<UUID, List<String>> messages = new LinkedHashMap<>();
        final Map<UUID, String> lastActionBar = new HashMap<>();
        final Set<UUID> online = new HashSet<>();
        final Set<UUID> inLobby = new HashSet<>();
        final Map<UUID, Team> launchedFighters = new LinkedHashMap<>();
        final Set<UUID> launchedSpectators = new HashSet<>();
        final Set<UUID> discardedInventories = new HashSet<>();

        /** Players whose lobby teleport fails, simulating a blocked destination. */
        final Set<UUID> lobbyTeleportFails = new HashSet<>();
        /** Players whose launch fails, simulating a Towny or teleport error. */
        final Set<UUID> launchFails = new HashSet<>();
        /** Players whose launch throws, simulating a Towny exception. */
        final Set<UUID> launchThrows = new HashSet<>();

        UUID join(String name) {
            UUID id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            online.add(id);
            inLobby.add(id);
            return id;
        }

        void leave(UUID playerId) {
            online.remove(playerId);
            inLobby.remove(playerId);
        }

        @Override
        public void broadcast(Component message) {
            broadcasts.add(PlainTextComponentSerializer.plainText().serialize(message));
        }

        @Override
        public void message(UUID playerId, Component message) {
            messages.computeIfAbsent(playerId, ignored -> new ArrayList<>())
                    .add(PlainTextComponentSerializer.plainText().serialize(message));
        }

        @Override
        public void actionBar(UUID playerId, Component message) {
            lastActionBar.put(playerId, PlainTextComponentSerializer.plainText().serialize(message));
        }

        @Override
        public boolean isOnline(UUID playerId) {
            return online.contains(playerId);
        }

        @Override
        public String nameOf(UUID playerId) {
            return "player-" + playerId.toString().substring(0, 8);
        }

        @Override
        public String worldOf(UUID playerId) {
            return inLobby.contains(playerId) ? "lobby" : "battlefield";
        }

        @Override
        public List<UUID> onlinePlayers() {
            return List.copyOf(online);
        }

        @Override
        public boolean sendToLobby(UUID playerId) {
            if (lobbyTeleportFails.contains(playerId)) {
                return false;
            }
            launchedFighters.remove(playerId);
            launchedSpectators.remove(playerId);
            inLobby.add(playerId);
            return true;
        }

        @Override
        public boolean launchFighter(UUID playerId, Team team, ActiveRoundContext context) {
            if (launchThrows.contains(playerId)) {
                throw new IllegalStateException("Towny refused " + playerId);
            }
            if (launchFails.contains(playerId)) {
                return false;
            }
            inLobby.remove(playerId);
            launchedFighters.put(playerId, team);
            return true;
        }

        @Override
        public boolean launchSpectator(UUID playerId, ActiveRoundContext context) {
            if (launchFails.contains(playerId)) {
                return false;
            }
            inLobby.remove(playerId);
            launchedSpectators.add(playerId);
            return true;
        }

        @Override
        public void discardStoredRoundInventory(UUID playerId) {
            discardedInventories.add(playerId);
        }

        @Override
        public boolean isInLobby(UUID playerId) {
            return inLobby.contains(playerId);
        }
    }

    /** Scriptable clean-copy loader. */
    static final class FakeWorlds implements WorldLifecycle {

        final List<String> loaded = new ArrayList<>();
        final List<String> unloaded = new ArrayList<>();
        final List<String> discarded = new ArrayList<>();
        final Map<String, ActiveMapWorld> byWorldName = new LinkedHashMap<>();

        /** Map ids whose copy fails outright. */
        final Set<String> loadFails = new HashSet<>();
        /** Map ids whose copy never completes, simulating a hang. */
        final Set<String> loadHangs = new HashSet<>();
        /** Holds every copy so a test can queue players before activation. */
        boolean holdAllLoads;
        /** Map ids reported unusable by static admission. */
        final Map<String, List<String>> staticProblems = new HashMap<>();
        /** Map ids reported unusable once loaded. */
        final Map<String, List<String>> runtimeProblems = new HashMap<>();
        /** World names whose unload fails, simulating players still inside. */
        final Set<String> unloadFails = new HashSet<>();

        List<String> untracked = List.of();

        private int sequence;
        private final Deque<PendingLoad> pending = new ArrayDeque<>();

        @Override
        public CompletableFuture<ActiveMapWorld> load(SiegeMap map) {
            loaded.add(map.id());
            if (loadFails.contains(map.id())) {
                return CompletableFuture.failedFuture(new IllegalStateException("copy failed for " + map.id()));
            }
            CompletableFuture<ActiveMapWorld> future = new CompletableFuture<>();
            if (holdAllLoads || loadHangs.contains(map.id())) {
                pending.add(new PendingLoad(map, future));
                return future;
            }
            future.complete(make(map, "siege-active-" + (++sequence) + "-" + map.id()));
            return future;
        }

        /** Completes the oldest held copy, using the given world-name prefix. */
        void releaseNextLoad(String prefix) {
            if (pending.isEmpty()) {
                return;
            }
            PendingLoad next = pending.removeFirst();
            next.future().complete(make(next.map(), prefix + (++sequence) + "-" + next.map().id()));
        }

        boolean hasHeldLoads() {
            return !pending.isEmpty();
        }

        private record PendingLoad(SiegeMap map, CompletableFuture<ActiveMapWorld> future) {
        }

        ActiveMapWorld make(SiegeMap map, String worldName) {
            ActiveMapWorld world = new ActiveMapWorld(map, world(worldName), Path.of("/tmp", worldName));
            byWorldName.put(worldName, world);
            return world;
        }

        @Override
        public CompletableFuture<ActiveMapWorld> resume(SiegeMap map, String runtimeWorldName) {
            ActiveMapWorld existing = byWorldName.get(runtimeWorldName);
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }
            if (loadFails.contains(map.id())) {
                return CompletableFuture.failedFuture(new IllegalStateException("missing " + runtimeWorldName));
            }
            return CompletableFuture.completedFuture(make(map, runtimeWorldName));
        }

        @Override
        public CompletableFuture<Void> unload(ActiveMapWorld activeWorld) {
            String name = activeWorld.world().getName();
            if (unloadFails.contains(name)) {
                return CompletableFuture.failedFuture(new IllegalStateException("players remain in " + name));
            }
            unloaded.add(name);
            byWorldName.remove(name);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> discard(String runtimeWorldName, String folder) {
            if (unloadFails.contains(runtimeWorldName)) {
                return CompletableFuture.failedFuture(new IllegalStateException("players remain"));
            }
            discarded.add(runtimeWorldName);
            byWorldName.remove(runtimeWorldName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public List<String> staticProblems(SiegeMap map) {
            return staticProblems.getOrDefault(map.id(), List.of());
        }

        @Override
        public List<String> loadedCopyProblems(ActiveMapWorld world) {
            return runtimeProblems.getOrDefault(world.map().id(), List.of());
        }

        @Override
        public List<String> untrackedGeneratedWorlds() {
            return untracked;
        }
    }

    static {
        Objects.requireNonNull(Team.RED);
    }
}
