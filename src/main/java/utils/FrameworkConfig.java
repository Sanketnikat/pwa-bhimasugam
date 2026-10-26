package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import factory.Platform;

/**
 * Settings from {@code config.properties} on the classpath. A non-empty JVM system
 * property with the same key wins, so any value can be overridden from the command
 * line, e.g. {@code -Dheadless=false}.
 */
public final class FrameworkConfig {

    private static final Properties FILE = load();

    private FrameworkConfig() {
    }

    public static String baseUrl() {
        String url = get("base.url");
        return url.endsWith("/") ? url : url + "/";
    }

    public static Platform defaultPlatform() {
        return Platform.from(get("platform"));
    }

    public static boolean headless() {
        return Boolean.parseBoolean(get("headless"));
    }

    public static double timeoutMs() {
        return Double.parseDouble(get("timeout.ms"));
    }

    public static double appStartTimeoutMs() {
        return Double.parseDouble(get("app.start.timeout.ms"));
    }

    public static int appStartAttempts() {
        return Integer.parseInt(get("app.start.attempts"));
    }

    public static boolean videoEnabled() {
        return Boolean.parseBoolean(get("video.enabled"));
    }

    /** This run's video folder: {@code video.dir/<run start time>}, so earlier runs' videos are kept. */
    public static Path videoDir() {
        return Path.of(get("video.dir"), runStamp());
    }

    /** Start time of this test run, e.g. "20261024-180512"; the same value for the whole run. */
    public static synchronized String runStamp() {
        if (runStamp == null) {
            runStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        }
        return runStamp;
    }

    private static String runStamp;

    public static Path artifactsDir() {
        return Path.of(get("artifacts.dir"));
    }

    public static String get(String key) {
        String override = System.getProperty(key);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String value = FILE.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing setting '" + key + "' in config.properties");
        }
        return value.trim();
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = FrameworkConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new IllegalStateException("config.properties not found on the classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return props;
    }
}
