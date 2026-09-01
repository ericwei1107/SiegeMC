package woo.siegePlugin.map;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapValidatorTest {

    @Test
    void aFullyConfiguredMapWithAReadableTemplateIsAdmitted() {
        assertEquals(List.of(), MapValidator.staticProblems(map(
                point(-100, 70, -100), point(100, 70, 100), point(0, 70, 0), 16
        ), true));
    }

    @Test
    void anUnreadableTemplateBlocksAdmission() {
        List<String> problems = MapValidator.staticProblems(map(
                point(-100, 70, -100), point(100, 70, 100), point(0, 70, 0), 16
        ), false);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("template folder"), problems.toString());
    }

    @Test
    void spawnsOutsideTheArenaBoundaryAreReported() {
        List<String> problems = MapValidator.staticProblems(map(
                point(-900, 70, 0), point(0, 70, 900), point(0, 70, 0), 16
        ), true);
        assertTrue(problems.contains("red-spawn is outside bounds"), problems.toString());
        assertTrue(problems.contains("blue-spawn is outside bounds"), problems.toString());
    }

    @Test
    void identicalTeamSpawnsAreRejected() {
        List<String> problems = MapValidator.staticProblems(map(
                point(10, 70, 10), point(10, 70, 10), point(0, 70, 0), 16
        ), true);
        assertTrue(problems.contains("red-spawn and blue-spawn are the same position"), problems.toString());
    }

    @Test
    void aCaptureRadiusReachingOutsideBoundsIsRejected() {
        List<String> problems = MapValidator.staticProblems(map(
                point(-100, 70, -100), point(100, 70, 100), point(250, 70, 0), 16
        ), true);
        assertTrue(
                problems.stream().anyMatch(problem -> problem.contains("capture-point radius")),
                problems.toString()
        );
    }

    @Test
    void containsIsInclusiveAtEveryEdge() {
        MapBounds bounds = new MapBounds(-256, -256, 256, 256);
        assertTrue(MapValidator.contains(bounds, -256, 256));
        assertTrue(MapValidator.contains(bounds, 256, -256));
        assertEquals(false, MapValidator.contains(bounds, 256.5, 0));
    }

    @Test
    void everyClaimedNativeChunkMustFitEntirelyInsideArenaBounds() {
        SiegeMap map = new SiegeMap(
                "kazan", "Siege of Kazan", "kazan",
                point(-100, 70, -100), point(100, 70, 100), point(0, 70, 0), 16,
                new MapBounds(-16, -16, 30, 30),
                Set.of(new BaseClaim(Team.RED, -1, -1), new BaseClaim(Team.BLUE, 1, 1))
        );

        List<String> problems = MapValidator.staticProblems(map, true);

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("Blue Team base claim at chunk 1, 1")),
                problems.toString());
        assertTrue(problems.stream().noneMatch(problem -> problem.contains("Red Team base claim")),
                problems.toString());
    }

    @Test
    void missingTeamClaimsAreWarningsRatherThanAdmissionFailures() {
        SiegeMap map = map(
                point(-100, 70, -100), point(100, 70, 100), point(0, 70, 0), 16
        );

        assertEquals(List.of(), MapValidator.staticProblems(map, true));
        assertEquals(2, MapValidator.baseClaimWarnings(map).size());
    }

    private static SiegeMap map(MapPoint red, MapPoint blue, MapPoint capture, int radius) {
        return new SiegeMap(
                "kazan", "Siege of Kazan", "kazan", red, blue, capture, radius,
                new MapBounds(-256, -256, 256, 256)
        );
    }

    private static MapPoint point(double x, double y, double z) {
        return new MapPoint(x, y, z, 0f, 0f);
    }
}
