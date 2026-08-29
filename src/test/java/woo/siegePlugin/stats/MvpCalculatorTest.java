package woo.siegePlugin.stats;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MvpCalculatorTest {

    @Test
    void overallUsesApprovedCrossTeamContributionWeights() {
        PlayerMatchStats banner = stats("00000000-0000-0000-0000-000000000001", "Banner", 0, 0, 90);
        PlayerMatchStats killer = stats("00000000-0000-0000-0000-000000000002", "Killer", 6, 0, 0);
        PlayerMatchStats damage = stats("00000000-0000-0000-0000-000000000003", "Damage", 4, 1000, 0);

        MvpResults result = MvpCalculator.calculate(List.of(banner, killer, damage));

        assertEquals("Banner", result.bannerMvp().orElseThrow().playerName());
        assertEquals("Killer", result.killsMvp().orElseThrow().playerName());
        assertEquals("Damage", result.damageMvp().orElseThrow().playerName());
        assertEquals("Banner", result.overallMvp().orElseThrow().playerName());
    }

    @Test
    void noContributorsProducesNoMvps() {
        MvpResults result = MvpCalculator.calculate(List.of(stats(
                "00000000-0000-0000-0000-000000000001", "Idle", 0, 0, 0
        )));
        assertTrue(result.killsMvp().isEmpty());
        assertTrue(result.damageMvp().isEmpty());
        assertTrue(result.bannerMvp().isEmpty());
        assertTrue(result.overallMvp().isEmpty());
    }

    @Test
    void exactTiesResolveToLexicallySmallestUuid() {
        PlayerMatchStats smaller = stats("00000000-0000-0000-0000-000000000001", "First", 2, 10, 5);
        PlayerMatchStats larger = stats("00000000-0000-0000-0000-000000000002", "Second", 2, 10, 5);

        MvpResults result = MvpCalculator.calculate(List.of(larger, smaller));

        assertEquals(smaller.playerId(), result.overallMvp().orElseThrow().playerId());
        assertEquals(smaller.playerId(), result.killsMvp().orElseThrow().playerId());
    }

    private static PlayerMatchStats stats(String id, String name, long kills, double damage, long banner) {
        return new PlayerMatchStats(UUID.fromString(id), name, kills, damage, banner);
    }
}
