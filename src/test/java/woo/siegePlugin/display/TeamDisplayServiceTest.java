package woo.siegePlugin.display;

import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TeamDisplayServiceTest {

    @Test
    void classifiesPlayersRelativeToAPlayerWithATeam() {
        assertEquals(
                TeamDisplayService.DisplayGroup.FRIENDLY,
                TeamDisplayService.classify(Team.RED, Team.RED)
        );
        assertEquals(
                TeamDisplayService.DisplayGroup.ENEMY,
                TeamDisplayService.classify(Team.RED, Team.BLUE)
        );
        assertEquals(
                TeamDisplayService.DisplayGroup.NONE,
                TeamDisplayService.classify(Team.RED, null)
        );
    }

    @Test
    void classifiesAbsoluteColorsForAViewerWithoutATeam() {
        assertEquals(
                TeamDisplayService.DisplayGroup.DISPLAY_RED,
                TeamDisplayService.classify(null, Team.RED)
        );
        assertEquals(
                TeamDisplayService.DisplayGroup.DISPLAY_BLUE,
                TeamDisplayService.classify(null, Team.BLUE)
        );
    }

    @Test
    void parsesConfiguredIdentityColorsCaseInsensitively() {
        assertEquals(NamedTextColor.RED, TeamIdentityColors.parse("RED"));
        assertEquals(NamedTextColor.DARK_AQUA, TeamIdentityColors.parse("dark_aqua"));
        assertNull(TeamIdentityColors.parse("not-a-color"));
    }
}
