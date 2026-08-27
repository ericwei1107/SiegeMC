package woo.siegePlugin.minecart;

import woo.siegePlugin.team.Team;

/** Pure form of the approved flat seven-player comeback rule. */
public final class MinecartDamagePolicy {

    private final MinecartDamageSettings settings;

    public MinecartDamagePolicy(MinecartDamageSettings settings) {
        this.settings = settings;
    }

    public boolean usesFullVanillaDamage(Team victimTeam, MinecartHeadcounts headcounts) {
        int victimLead = headcounts.forTeam(victimTeam) - headcounts.against(victimTeam);
        return victimLead >= settings.fullDamageDeficit();
    }

    public double scaledRawDamage(double vanillaRawDamage, Team victimTeam, MinecartHeadcounts headcounts) {
        if (usesFullVanillaDamage(victimTeam, headcounts)) {
            return vanillaRawDamage;
        }
        return vanillaRawDamage * settings.balancedCoefficient();
    }
}
