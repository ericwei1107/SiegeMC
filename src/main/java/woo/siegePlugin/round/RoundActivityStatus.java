package woo.siegePlugin.round;

/** Gameplay gate: true only while one fully published round is active. */
@FunctionalInterface
public interface RoundActivityStatus {
    boolean isActive();
}
