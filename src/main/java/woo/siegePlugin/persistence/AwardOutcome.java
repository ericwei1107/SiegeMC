package woo.siegePlugin.persistence;

import java.util.Objects;

/** Result of an atomic score award and possible match completion. */
public record AwardOutcome(boolean accepted, boolean completedNow, MatchRecord match) {

    public AwardOutcome {
        match = Objects.requireNonNull(match, "match");
        if (completedNow && !accepted) {
            throw new IllegalArgumentException("A rejected award cannot complete a match");
        }
    }

    public static AwardOutcome rejected(MatchRecord match) {
        return new AwardOutcome(false, false, match);
    }
}
