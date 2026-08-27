package woo.siegePlugin.minecart;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Tunable Stage 4.5 damage policy. */
public record MinecartDamageSettings(double balancedCoefficient, int fullDamageDeficit) {

    static final String COEFFICIENT_PATH = "minecart.damage.balanced-coefficient";
    static final String DEFICIT_PATH = "minecart.damage.full-damage-deficit";

    private static final double DEFAULT_COEFFICIENT = 0.825D;
    private static final int DEFAULT_FULL_DAMAGE_DEFICIT = 7;

    public MinecartDamageSettings {
        if (!Double.isFinite(balancedCoefficient) || balancedCoefficient <= 0.0D) {
            throw new IllegalArgumentException("Balanced minecart damage coefficient must be finite and positive");
        }
        if (fullDamageDeficit <= 0) {
            throw new IllegalArgumentException("Full-damage deficit must be positive");
        }
    }

    public static MinecartDamageSettings fromConfig(FileConfiguration config) {
        return new MinecartDamageSettings(
                config.getDouble(COEFFICIENT_PATH, DEFAULT_COEFFICIENT),
                config.getInt(DEFICIT_PATH, DEFAULT_FULL_DAMAGE_DEFICIT)
        );
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();

        if (config.isSet(COEFFICIENT_PATH)) {
            Object configured = config.get(COEFFICIENT_PATH);
            if (!(configured instanceof Number number)
                    || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() <= 0.0D) {
                problems.add(COEFFICIENT_PATH + " must be a finite positive number");
            }
        }

        if (config.isSet(DEFICIT_PATH)) {
            Object configured = config.get(DEFICIT_PATH);
            if (!(configured instanceof Number number)
                    || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() != Math.rint(number.doubleValue())
                    || number.doubleValue() <= 0.0D
                    || number.doubleValue() > Integer.MAX_VALUE) {
                problems.add(DEFICIT_PATH + " must be a positive whole number");
            }
        }

        return problems;
    }
}
