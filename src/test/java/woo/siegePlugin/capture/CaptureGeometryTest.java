package woo.siegePlugin.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureGeometryTest {

    private static final int RADIUS = 16;

    private static boolean within(double x, double y, double z) {
        return CaptureGeometry.isWithinCaptureZone(x, y, z, 0, 64, 0, RADIUS);
    }

    @Test
    void acceptsHorizontalDistanceExactlyAtTheRadius() {
        assertTrue(within(16, 64, 0));
    }

    @Test
    void rejectsHorizontalDistanceBeyondTheRadius() {
        assertFalse(within(16.01, 64, 0));
    }

    @Test
    void measuresHorizontalDistanceAsEuclideanNotPerAxis() {
        // 12,12 is inside a 16-block square but outside a 16-block circle.
        assertFalse(within(12, 64, 12));
        assertTrue(within(11, 64, 11));
    }

    @Test
    void boundsVerticalDifferenceIndependentlyOfHorizontalDistance() {
        assertTrue(within(0, 80, 0));
        assertTrue(within(0, 48, 0));
        assertFalse(within(0, 80.01, 0));
        assertFalse(within(0, 47.99, 0));
    }

    @Test
    void appliesBothBoundsTogether() {
        assertTrue(within(10, 74, 10));
        assertFalse(within(10, 90, 10));
    }
}
