package oep.skycast.util;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {

    // Ensure resources are written/read relative to project root during development
    private static final String FAVORITES_PATH = "resources/favorites.txt";
    private static final String LASTCITY_PATH  = "resources/lastCity.txt";

    public static void saveFavorites(List<String> favs) throws IOException {
        Path path = Paths.get(FAVORITES_PATH);
        Files.createDirectories(path.getParent());
        try (BufferedWriter bw = Files.newBufferedWriter(path)) {
            for (String s : favs) {
                bw.write(s);
                bw.newLine();
            }
        }
    }

    public static List<String> loadFavorites() throws IOException {
        Path path = Paths.get(FAVORITES_PATH);
        List<String> result = new ArrayList<>();
        if (!Files.exists(path)) return result;
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) result.add(line);
            }
        }
        return result;
    }

    public static void saveLastCity(String city) throws IOException {
        Path path = Paths.get(LASTCITY_PATH);
        Files.createDirectories(path.getParent());
        try (BufferedWriter bw = Files.newBufferedWriter(path)) {
            bw.write(city == null ? "" : city);
        }
    }

    public static String loadLastCity() throws IOException {
        Path path = Paths.get(LASTCITY_PATH);
        if (!Files.exists(path)) return "";
        return Files.readString(path).trim();
    }
}
