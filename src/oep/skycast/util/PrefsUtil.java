package oep.skycast.util;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Minimal preferences utility that stores preferences in resources/preferences.properties
 * (path: ./resources/preferences.properties). Creates file if missing.
 */
public class PrefsUtil {

    private static final Path PREFS_PATH = Paths.get("resources", "preferences.properties");
    private static final Properties props = new Properties();
    static {
        try {
            if (Files.exists(PREFS_PATH)) {
                try (InputStream is = Files.newInputStream(PREFS_PATH)) {
                    props.load(is);
                }
            } else {
                // ensure folder exists and create empty file
                Files.createDirectories(PREFS_PATH.getParent());
                Files.createFile(PREFS_PATH);
            }
        } catch (IOException ignored) {}
    }

    public static synchronized String get(String key, String def) {
        return props.getProperty(key, def);
    }

    public static synchronized void put(String key, String value) {
        props.setProperty(key, value);
        try (OutputStream os = Files.newOutputStream(PREFS_PATH)) {
            props.store(os, "SkyCast preferences");
        } catch (IOException ignored) {}
    }
}
