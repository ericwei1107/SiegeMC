package woo.siegePlugin.round;

import woo.siegePlugin.persistence.MatchDefinition;

import java.util.function.Consumer;

/**
 * The only thing the coordinator asks of scoring: open a match, or explain why
 * it could not. Narrowing the dependency to this is what lets the lifecycle be
 * tested without a Bukkit scheduler behind {@code ScoringService}.
 */
@FunctionalInterface
public interface RoundScoring {

    /** Completes with null on success, or the failure that blocked activation. */
    void activateMatch(MatchDefinition definition, Consumer<Throwable> completion);
}
