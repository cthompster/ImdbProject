package dataProviders;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

import enums.DriverType;
import enums.EnvironmentType;

/**
 * Reads {@code configuration.properties} from the test classpath.
 * A JVM system property with the same key (e.g. {@code -Dbrowser=firefox}) overrides the file.
 */
public class ConfigFileReader {
    private static final String RESOURCE = "configuration.properties";
    private final Properties properties = new Properties();

    public ConfigFileReader() {
        try (InputStream in = ConfigFileReader.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " was not found on the test classpath");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

    private String get(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = properties.getProperty(key);
        }
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String required(String key) {
        String value = get(key);
        if (value == null) {
            throw new IllegalStateException("'" + key + "' is not set in " + RESOURCE + " or as -D" + key);
        }
        return value;
    }

    private long getLong(String key, long defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("'" + key + "' must be a whole number but was: " + value, e);
        }
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public String getApplicationUrl() {
        String url = required("url");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public DriverType getBrowser() {
        String browser = get("browser");
        if (browser == null) return DriverType.CHROME;
        try {
            return DriverType.valueOf(browser.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unsupported browser '" + browser + "'. Use chrome, firefox or safari.", e);
        }
    }

    public EnvironmentType getEnvironment() {
        String environment = get("environment");
        if (environment == null) return EnvironmentType.LOCAL;
        try {
            return EnvironmentType.valueOf(environment.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unsupported environment '" + environment + "'. Use local or remote.", e);
        }
    }

    public boolean isHeadless() {
        return getBoolean("headless", false);
    }

    public boolean isWindowMaximize() {
        return getBoolean("windowMaximize", true);
    }

    public Duration getExplicitWait() {
        return Duration.ofSeconds(getLong("explicitWait", 15));
    }

    public Duration getCaptchaWait() {
        return Duration.ofSeconds(getLong("captchaWaitSeconds", 180));
    }

    public String getTitleId() {
        return required("titleId");
    }

    /** Absolute path of the Chrome profile directory, or {@code null} to use a throwaway profile. */
    public Path getChromeProfileDir() {
        String dir = get("chromeProfileDir");
        return dir == null ? null : Path.of(dir).toAbsolutePath();
    }
}
