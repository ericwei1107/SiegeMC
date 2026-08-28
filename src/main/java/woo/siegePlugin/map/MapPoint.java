package woo.siegePlugin.map;

/** A map-template coordinate, deliberately independent of a runtime World instance. */
public record MapPoint(double x, double y, double z, float yaw, float pitch) {
}
