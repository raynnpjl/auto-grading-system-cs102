package grading.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * A properties file of settings. The defaults are packaged with the application, and a copy
 * outside it overrides any of them without rebuilding: the path in the system property
 * {@code <name>.config}, otherwise {@code config/<name>.properties} in the working directory.
 */
public final class ConfigFile {

    private final String name;
    private final Properties values;

    private ConfigFile(String name, Properties values) {
        this.name = name;
        this.values = values;
    }

    /**
     * Reads {@code <name>.properties}: the packaged defaults, overridden by a copy outside the application.
     *
     * @throws IllegalStateException if the packaged file or the override cannot be read
     */
    public static ConfigFile load(String name) {
        Properties values = new Properties();
        String resource = "/" + name + ".properties";
        try (InputStream defaults = ConfigFile.class.getResourceAsStream(resource)) {
            if (defaults == null) {
                throw new IllegalStateException("Missing packaged settings file " + resource);
            }
            values.load(defaults);
            Path override = Path.of(System.getProperty(name + ".config", "config/" + name + ".properties"));
            if (Files.isRegularFile(override)) {
                try (Reader reader = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                    values.load(reader);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the " + name + " settings", exception);
        }
        return new ConfigFile(name, values);
    }

    /**
     * The value of a setting, which must be present and not blank.
     *
     * @throws IllegalStateException if the setting is missing
     */
    public String text(String key) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Setting " + key + " is missing from " + name + ".properties");
        }
        return value.strip();
    }

    /** A comma-separated list; an absent or blank setting is an empty list. */
    public List<String> list(String key) {
        String value = values.getProperty(key, "");
        return Arrays.stream(value.split(",")).map(String::strip).filter(item -> !item.isEmpty()).toList();
    }

    /** A number that is zero or more, such as a weight or a penalty. */
    public double nonNegativeDecimal(String key) {
        try {
            double number = Double.parseDouble(text(key));
            if (number >= 0 && Double.isFinite(number)) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // reported below with the setting's name
        }
        throw new IllegalStateException("Setting " + key + " in " + name
                + ".properties must be a number of zero or more");
    }

    /** A share from 0 to 1, such as 0.8 for 80%. */
    public double fraction(String key) {
        double number = nonNegativeDecimal(key);
        if (number > 1) {
            throw new IllegalStateException("Setting " + key + " in " + name
                    + ".properties must be between 0 and 1, such as 0.8 for 80%");
        }
        return number;
    }

    /**
     * The value of a setting that must be a whole number above zero.
     *
     * @throws IllegalStateException if the setting is missing or not a positive number
     */
    public long positiveNumber(String key) {
        try {
            long number = Long.parseLong(text(key));
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // reported below with the setting's name
        }
        throw new IllegalStateException("Setting " + key + " in " + name
                + ".properties must be a positive whole number");
    }
}
