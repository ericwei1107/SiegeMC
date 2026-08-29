package woo.siegePlugin.stats;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/** Deterministic category and weighted MVP selection. */
public final class MvpCalculator {

    private MvpCalculator() {
    }

    public static MvpResults calculate(Collection<PlayerMatchStats> input) {
        List<PlayerMatchStats> stats = List.copyOf(input);
        double totalDamage = stats.stream().mapToDouble(PlayerMatchStats::damage).sum();
        long totalKills = stats.stream().mapToLong(PlayerMatchStats::kills).sum();
        long totalBanner = stats.stream().mapToLong(PlayerMatchStats::bannerSeconds).sum();

        ToDoubleFunction<PlayerMatchStats> overall = value ->
                0.45D * share(value.bannerSeconds(), totalBanner)
                        + 0.45D * share(value.kills(), totalKills)
                        + 0.10D * share(value.damage(), totalDamage);

        Optional<PlayerMatchStats> overallMvp = stats.stream()
                .filter(value -> value.kills() > 0L || value.damage() > 0D || value.bannerSeconds() > 0L)
                .max(Comparator.comparingDouble(overall)
                        .thenComparingLong(PlayerMatchStats::bannerSeconds)
                        .thenComparingLong(PlayerMatchStats::kills)
                        .thenComparingDouble(PlayerMatchStats::damage)
                        .thenComparing(PlayerMatchStats::playerId, MvpCalculator::reverseUuid));

        return new MvpResults(
                category(stats, PlayerMatchStats::kills, overall),
                damageCategory(stats, overall),
                category(stats, PlayerMatchStats::bannerSeconds, overall),
                overallMvp
        );
    }

    private static Optional<PlayerMatchStats> category(
            List<PlayerMatchStats> stats,
            ToLongFunction<PlayerMatchStats> metric,
            ToDoubleFunction<PlayerMatchStats> overall
    ) {
        return stats.stream().filter(value -> metric.applyAsLong(value) > 0L)
                .max(Comparator.comparingLong(metric)
                        .thenComparingDouble(overall)
                        .thenComparing(PlayerMatchStats::playerId, MvpCalculator::reverseUuid));
    }

    private static Optional<PlayerMatchStats> damageCategory(
            List<PlayerMatchStats> stats,
            ToDoubleFunction<PlayerMatchStats> overall
    ) {
        return stats.stream().filter(value -> value.damage() > 0D)
                .max(Comparator.comparingDouble(PlayerMatchStats::damage)
                        .thenComparingDouble(overall)
                        .thenComparing(PlayerMatchStats::playerId, MvpCalculator::reverseUuid));
    }

    private static double share(double value, double total) {
        return total <= 0D ? 0D : value / total;
    }

    private static int reverseUuid(UUID left, UUID right) {
        return right.toString().compareTo(left.toString());
    }
}
