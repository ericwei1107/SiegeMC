package woo.siegePlugin.round;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import woo.siegePlugin.map.MapBounds;
import woo.siegePlugin.map.MapPoint;
import woo.siegePlugin.map.SiegeMap;
import woo.siegePlugin.team.Team;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The single rule that decides whether activity counts toward the round.
 *
 * <p>Every case here previously slipped through at least one system: a lobby
 * player could score, a player in another world could hold the banner, and an
 * unrostered player could earn currency.</p>
 */
class ActiveCombatEligibilityTest {

    private static final World BATTLEFIELD = world("siege-active-1-kazan");
    private static final World LOBBY = world("lobby");

    private ActiveRoundProvider rounds;
    private RoundRoster roster;
    private ActiveCombatEligibility eligibility;
    private UUID fighterId;
    private UUID spectatorId;

    @BeforeEach
    void setUp() {
        rounds = new ActiveRoundProvider();
        roster = new RoundRoster();
        eligibility = new ActiveCombatEligibility(rounds, roster);
        fighterId = UUID.randomUUID();
        spectatorId = UUID.randomUUID();
        rounds.restore(RoundPhase.ACTIVE, context());
        roster.bind("rotation-1", List.of(
                new RoundRoster.Membership(fighterId, "Fighter", Team.RED, RoundRole.PLAYER, RosterPresence.BATTLEFIELD),
                new RoundRoster.Membership(spectatorId, "Watcher", null, RoundRole.SPECTATOR, RosterPresence.BATTLEFIELD)
        ));
    }

    @Test
    void aRosteredFighterOnTheActiveMapIsEligible() {
        assertTrue(eligibility.isEligibleFighter(player(fighterId, BATTLEFIELD)));
        assertTrue(eligibility.isOnBattlefield(player(fighterId, BATTLEFIELD)));
        assertTrue(eligibility.activeMatchId().isPresent());
    }

    @Test
    void aFighterStandingInTheLobbyIsNotEligible() {
        assertFalse(eligibility.isEligibleFighter(player(fighterId, LOBBY)),
                "another world must never count as the battlefield");
    }

    @Test
    void aFighterWhoReturnedToTheLobbyIsNotEligibleEvenBackOnTheMap() {
        roster.setPresence(fighterId, RosterPresence.LOBBY);
        assertFalse(eligibility.isEligibleFighter(player(fighterId, BATTLEFIELD)));
    }

    @Test
    void anUnrosteredPlayerIsNotEligible() {
        assertFalse(eligibility.isEligibleFighter(player(UUID.randomUUID(), BATTLEFIELD)));
    }

    @Test
    void aSpectatorIsOnTheBattlefieldButIsNeverAnEligibleFighter() {
        Player spectator = player(spectatorId, BATTLEFIELD);
        assertTrue(eligibility.isOnBattlefield(spectator));
        assertFalse(eligibility.isEligibleFighter(spectator),
                "a spectator must not score, capture, or earn currency");
    }

    @Test
    void nothingIsEligibleOutsideAnActiveRound() {
        for (RoundPhase phase : List.of(
                RoundPhase.BOOTSTRAPPING, RoundPhase.COMPLETING,
                RoundPhase.INTERMISSION, RoundPhase.ACTIVATING, RoundPhase.RECOVERY
        )) {
            rounds.restore(phase, context());
            assertFalse(eligibility.isEligibleFighter(player(fighterId, BATTLEFIELD)),
                    "no activity may count during " + phase);
            assertTrue(eligibility.activeMatchId().isEmpty());
        }
    }

    @Test
    void aUuidOnlyCallerFollowsTheSameRule() {
        assertTrue(eligibility.isEligibleFighter(fighterId, "siege-active-1-kazan"));
        assertFalse(eligibility.isEligibleFighter(fighterId, "lobby"));
        assertFalse(eligibility.isEligibleFighter(spectatorId, "siege-active-1-kazan"));
    }

    private static ActiveRoundContext context() {
        MapPoint red = new MapPoint(-10, 70, -10, 0, 0);
        MapPoint blue = new MapPoint(10, 70, 10, 0, 0);
        MapPoint capture = new MapPoint(0, 70, 0, 0, 0);
        SiegeMap map = new SiegeMap(
                "kazan", "Siege of Kazan", "kazan", red, blue, capture, 16,
                new MapBounds(-256, -256, 256, 256)
        );
        return new ActiveRoundContext(
                1L, "rotation-1", map, BATTLEFIELD, 10_000L,
                Map.of(
                        Team.RED, new org.bukkit.Location(BATTLEFIELD, -10, 70, -10),
                        Team.BLUE, new org.bukkit.Location(BATTLEFIELD, 10, 70, 10)
                ),
                new org.bukkit.Location(BATTLEFIELD, 0, 70, 0),
                map.bounds()
        );
    }

    private static Player player(UUID id, World world) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getWorld" -> world;
                    case "getName" -> "player";
                    case "isOnline" -> true;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Player[" + id + "]";
                    default -> null;
                }
        );
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "World[" + name + "]";
                    default -> null;
                }
        );
    }
}
