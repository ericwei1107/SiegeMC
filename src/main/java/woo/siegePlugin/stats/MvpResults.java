package woo.siegePlugin.stats;

import java.util.Optional;

public record MvpResults(
        Optional<PlayerMatchStats> killsMvp,
        Optional<PlayerMatchStats> damageMvp,
        Optional<PlayerMatchStats> bannerMvp,
        Optional<PlayerMatchStats> overallMvp
) {
}
