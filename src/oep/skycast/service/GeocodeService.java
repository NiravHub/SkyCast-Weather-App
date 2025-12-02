package oep.skycast.service;

import com.google.gson.*;
import oep.skycast.util.PrefsUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GeocodeService - wrapper that provides place autocomplete/suggestions.
 *
 * Behavior:
 *  - If weather.api.key exists in PrefsUtil, calls WeatherAPI search endpoint:
 *      https://api.weatherapi.com/v1/search.json?key=KEY&q=...
 *  - Otherwise falls back to OpenStreetMap Nominatim:
 *      https://nominatim.openstreetmap.org/search?format=json&limit=10&q=...
 *
 * Returns up to 10 suggestions as DisplayPlace objects.
 */
public class GeocodeService {

    private static final String WEATHERAPI_SEARCH = "https://api.weatherapi.com/v1/search.json";
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search?format=json&limit=10&q=";

    private final HttpClient client;
    private final Gson gson = new Gson();
    private final String apiKey;
    private final boolean useWeatherApi;

    public GeocodeService() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        String key = PrefsUtil.get("weather.api.key", "").trim();
        this.apiKey = key == null ? "" : key;
        this.useWeatherApi = !this.apiKey.isBlank();
    }

    /**
     * Friendly alias — controller expects suggest(...)
     */
    public List<DisplayPlace> suggest(String q) throws IOException, InterruptedException {
        return search(q);
    }

    /**
     * Search/suggest places for the given query.
     * Returns an empty list on error or no results.
     */
    public List<DisplayPlace> search(String q) throws IOException, InterruptedException {
        if (q == null) return Collections.emptyList();
        String query = q.trim();
        if (query.isEmpty()) return Collections.emptyList();

        try {
            if (useWeatherApi) {
                return searchWeatherApi(query);
            } else {
                return searchNominatim(query);
            }
        } catch (IOException | InterruptedException ex) {
            // bubble up network exceptions to caller if they want to handle them,
            // but also allow fallback: if WeatherAPI failed and we have no API key (or even if we do),
            // attempt Nominatim as a fallback (best-effort).
            if (useWeatherApi) {
                try {
                    return searchNominatim(query);
                } catch (Exception ignored) {}
            }
            throw ex;
        } catch (Exception ex) {
            // any other parsing exception -> return empty list
            return Collections.emptyList();
        }
    }

    // WeatherAPI search.json: returns array of objects { "id","name","region","country","lat","lon" ... }
    private List<DisplayPlace> searchWeatherApi(String q) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
        String url = String.format("%s?key=%s&q=%s", WEATHERAPI_SEARCH, apiKey, encoded);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", "SkyCast-StudentOEP/1.0 (+https://example.local)")
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return Collections.emptyList();

        JsonElement root = JsonParser.parseString(resp.body());
        if (!root.isJsonArray()) return Collections.emptyList();

        JsonArray arr = root.getAsJsonArray();
        List<DisplayPlace> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();

            String name = safeString(o, "name");
            String region = safeString(o, "region");
            String country = safeString(o, "country");
            double lat = safeDoubleFromElement(o, "lat");
            double lon = safeDoubleFromElement(o, "lon");

            String display;
            if (!isBlank(region)) display = String.format("%s, %s, %s", name, region, country);
            else if (!isBlank(country)) display = String.format("%s, %s", name, country);
            else display = name != null ? name : "";

            out.add(new DisplayPlace(display, name, region, country, lat, lon));
            if (out.size() >= 10) break;
        }

        return out;
    }

    // Nominatim search: returns array of objects { "display_name", "lat", "lon", ... }
    private List<DisplayPlace> searchNominatim(String q) throws IOException, InterruptedException {
        String url = NOMINATIM_URL + URLEncoder.encode(q, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", "SkyCast-StudentOEP/1.0 (+https://example.local)")
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return Collections.emptyList();

        JsonElement root = JsonParser.parseString(resp.body());
        if (!root.isJsonArray()) return Collections.emptyList();

        JsonArray arr = root.getAsJsonArray();
        List<DisplayPlace> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();

            String displayName = safeString(o, "display_name");
            double lat = safeDoubleFromElement(o, "lat");
            double lon = safeDoubleFromElement(o, "lon");

            // Nominatim doesn't provide region/country in top-level consistently — keep them null if unknown
            String name = null, region = null, country = null;
            // Optionally try to extract last parts of display_name as country/region (rudimentary)
            if (!isBlank(displayName)) {
                String[] parts = displayName.split(",\\s*");
                if (parts.length >= 1) name = parts[0];
                if (parts.length >= 2) region = parts[parts.length - 2];
                if (parts.length >= 1) country = parts[parts.length - 1];
            }

            out.add(new DisplayPlace(displayName, name, region, country, lat, lon));
            if (out.size() >= 10) break;
        }

        return out;
    }

    // utilities
    private static double safeDoubleFromElement(JsonObject o, String key) {
        try {
            if (o.has(key) && !o.get(key).isJsonNull()) {
                // WeatherAPI sometimes returns numbers; nominatim returns strings for lat/lon
                JsonElement el = o.get(key);
                if (el.isJsonPrimitive()) {
                    JsonPrimitive p = el.getAsJsonPrimitive();
                    if (p.isNumber()) return p.getAsDouble();
                    if (p.isString()) return Double.parseDouble(p.getAsString());
                }
            }
        } catch (Exception ignored) {}
        return Double.NaN;
    }

    private static String safeString(JsonObject o, String key) {
        try {
            if (o.has(key) && !o.get(key).isJsonNull()) return o.get(key).getAsString();
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    // DisplayPlace used by controller (has getters & toString)
    public static class DisplayPlace {
        public final String displayName;
        public final String name;
        public final String region;
        public final String country;
        public final double lat;
        public final double lon;

        public DisplayPlace(String displayName, String name, String region, String country, double lat, double lon) {
            this.displayName = displayName;
            this.name = name;
            this.region = region;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
        }

        public String getDisplayName() { return displayName; }
        public String getName() { return name; }
        public String getRegion() { return region; }
        public String getCountry() { return country; }
        public double getLat() { return lat; }
        public double getLon() { return lon; }

        @Override
        public String toString() {
            return displayName == null ? (name == null ? "" : name) : displayName;
        }
    }
}
