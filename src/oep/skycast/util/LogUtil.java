package oep.skycast.util;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogUtil {
    private static final Path LOG_PATH = Paths.get("resources", "logs", "weather-log.txt");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static synchronized void log(String message) {
        try {
            Files.createDirectories(LOG_PATH.getParent());
            String line = String.format("[%s] %s%n", LocalDateTime.now().format(FMT), message);
            Files.writeString(LOG_PATH, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // If logging fails, do not crash the app
        }
    }
}
