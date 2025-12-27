package com.azuredoom.hyleveling.config;

import com.azuredoom.hyleveling.HyLevelingException;
import com.azuredoom.hyleveling.level.formulas.ExponentialLevelFormula;
import com.azuredoom.hyleveling.level.formulas.LevelFormula;
import com.azuredoom.hyleveling.level.formulas.LinearLevelFormula;
import com.azuredoom.hyleveling.level.formulas.loader.LevelTableLoader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A factory class for creating instances of {@link LevelFormula} and related objects based on configuration or
 * descriptors. This class supports dynamic initialization of different implementations of {@link LevelFormula}, such as
 * exponential or linear formulas, depending on the input.
 * <p>
 * This class is utility-based and cannot be instantiated.
 */
public final class LevelFormulaFactory {

    private LevelFormulaFactory() {}

    /**
     * Constructs a {@link LevelFormula} object based on the provided configuration. The method determines the type of
     * formula to use (e.g., "EXPONENTIAL", "LINEAR", "TABLE") and initializes the appropriate implementation with the
     * parameters specified in the configuration. If the configuration is null or invalid, a default
     * {@link ExponentialLevelFormula} is returned.
     *
     * @param config the configuration object containing the formula type and its associated parameters. Must not be
     *               null and must specify a valid type ("EXPONENTIAL" or "LINEAR").
     * @return an instance of {@link LevelFormula}, either {@link ExponentialLevelFormula} or
     *         {@link LinearLevelFormula}, depending on the type specified in the configuration.
     * @throws HyLevelingException if the specified formula type is unknown or unsupported.
     */
    public static LevelFormula fromConfig(HyLevelingConfig config) {
        if (config == null || config.formula == null || config.formula.type == null) {
            return new ExponentialLevelFormula(100, 1.7);
        }

        String type = config.formula.type.trim().toUpperCase(Locale.ROOT);

        return switch (type) {
            case "EXPONENTIAL" -> {
                double baseXp = config.formula.exponential.baseXp;
                double exponent = config.formula.exponential.exponent;
                yield new ExponentialLevelFormula(baseXp, exponent);
            }
            case "LINEAR" -> {
                long xpPerLevel = config.formula.linear.xpPerLevel;
                yield new LinearLevelFormula(xpPerLevel);
            }
            case "TABLE" -> {
                var dataDir = Paths.get("./data");
                System.out.println("Data dir = " + dataDir.toAbsolutePath());
                yield LevelTableLoader.loadOrCreateFromDataDir(dataDir, config.formula.table.file);
            }
            default -> throw new HyLevelingException(
                "Unknown formula.type '" + config.formula.type + "'. Expected EXPONENTIAL or LINEAR."
            );
        };
    }

    /**
     * Constructs a {@link FormulaDescriptor} based on the provided {@link HyLevelingConfig}. The type and parameters
     * for the descriptor are determined by the configuration's formula settings. Supports the following formula types:
     * "EXPONENTIAL", "LINEAR", and "TABLE". Throws an exception if an unsupported formula type is specified.
     *
     * @param cfg the configuration object containing the formula type and its relevant parameter values
     * @return a {@link FormulaDescriptor} instance encapsulating the formula type and its parameters
     * @throws HyLevelingException if the formula type specified in the configuration is unknown
     */
    public static FormulaDescriptor descriptorFromConfig(HyLevelingConfig cfg) {
        var type = cfg.formula.type.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (type) {
            case "EXPONENTIAL" -> new FormulaDescriptor(
                "EXPONENTIAL",
                "baseXp=" + cfg.formula.exponential.baseXp + ";exponent=" + cfg.formula.exponential.exponent
            );
            case "LINEAR" -> new FormulaDescriptor(
                "LINEAR",
                "xpPerLevel=" + cfg.formula.linear.xpPerLevel
            );
            case "TABLE" -> new FormulaDescriptor(
                "TABLE",
                "file=" + cfg.formula.table.file
            );
            default -> throw new HyLevelingException("Unknown formula.type: " + cfg.formula.type);
        };
    }

    /**
     * Converts a {@link FormulaDescriptor} into a {@link LevelFormula} based on the descriptor's type and parameters.
     * The supported formula types are "EXPONENTIAL", "LINEAR", and "TABLE", each with specific parameter requirements.
     *
     * @param d the formula descriptor containing the type and parameters for constructing the level formula
     * @return a {@link LevelFormula} instance constructed according to the descriptor
     * @throws HyLevelingException if the descriptor contains an unknown formula type
     */
    public static LevelFormula formulaFromDescriptor(FormulaDescriptor d, Path dataDir) {
        var type = d.type().trim().toUpperCase(Locale.ROOT);

        Map<String, String> map = new HashMap<>();
        if (d.params() != null && !d.params().isBlank()) {
            for (var part : d.params().split(";")) {
                var kv = part.split("=", 2);
                if (kv.length == 2)
                    map.put(kv[0].trim(), kv[1].trim());
            }
        }

        return switch (type) {
            case "EXPONENTIAL" -> new ExponentialLevelFormula(
                Double.parseDouble(map.getOrDefault("baseXp", "100.0")),
                Double.parseDouble(map.getOrDefault("exponent", "1.7"))
            );
            case "LINEAR" -> new LinearLevelFormula(
                Long.parseLong(map.getOrDefault("xpPerLevel", "100"))
            );
            case "TABLE" -> {
                String file = map.getOrDefault("file", "levels.csv");
                yield LevelTableLoader.loadOrCreateFromDataDir(dataDir, file);
            }
            default -> throw new HyLevelingException("Unknown stored formula.type: " + d.type());
        };
    }
}
