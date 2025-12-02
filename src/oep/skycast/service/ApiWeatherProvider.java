package oep.skycast.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import oep.skycast.exceptions.WeatherException;
import oep.skycast.model.ForecastDay;
import oep.skycast.model.HourlyWeather;
import oep.skycast.model.WeatherData;
import oep.skycast.util.PrefsUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ApiWeatherProvider — integration with weatherapi.com
 *
 * Requires preference key: weather.api.key
 */
public class ApiWeatherProvider implements WeatherProvider {

    private static final String BASE_CURRENT = "https://api.weatherapi.com/v1/current.json";
    private static final String BASE_FORECAST = "https://api.weatherapi.com/v1/forecast.json";
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
    private final Gson gson = new Gson();
    private final String apiKey;

    public ApiWeatherProvider() {
        this.apiKey = PrefsUtil.get("weather.api.key", "").trim();
        if (this.apiKey.isEmpty()) {
            throw new IllegalStateException("weather.api.key missing in preferences");
        }
    }

    @Override
    public WeatherData getCurrentWeather(String city) throws WeatherException {
        try {
            String url = String.format("%s?key=%s&q=%s&aqi=yes", BASE_CURRENT, apiKey, encode(city));

            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw parseApiError(resp.body());
            }

            JsonObject root = gson.fromJson(resp.body(), JsonObject.class);
            JsonObject location = root.has("location") && root.get("location").isJsonObject()
                    ? root.getAsJsonObject("location") : null;
            JsonObject current = root.has("current") && root.get("current").isJsonObject()
                    ? root.getAsJsonObject("current") : null;

            if (current == null) throw new WeatherException("Invalid API response (missing current)");

            JsonObject cond = current.has("condition") && current.get("condition").isJsonObject()
                    ? current.getAsJsonObject("condition") : null;

            double tempC = getDoubleSafe(current, "temp_c", Double.NaN);
            double feels = getDoubleSafe(current, "feelslike_c", Double.NaN);
            int humidity = getIntSafe(current, "humidity", -1);
            double windKph = getDoubleSafe(current, "wind_kph", Double.NaN);
            double pressureMb = getDoubleSafe(current, "pressure_mb", Double.NaN);
            double visKm = getDoubleSafe(current, "vis_km", Double.NaN);
            double uv = getDoubleSafe(current, "uv", Double.NaN);
            int cloud = getIntSafe(current, "cloud", -1);

            String condText = cond != null && cond.has("text") ? cond.get("text").getAsString() : null;
            // weatherapi icons are sometimes like "//cdn.weatherapi.com/..." -> ensure https:
            String iconUrl = null;
            if (cond != null && cond.has("icon")) {
                iconUrl = cond.get("icon").getAsString();
                if (iconUrl != null && iconUrl.startsWith("//")) iconUrl = "https:" + iconUrl;
            }

            // location fields
            String locName = (location != null && location.has("name")) ? location.get("name").getAsString() : null;
            String region = (location != null && location.has("region")) ? location.get("region").getAsString() : null;
            String country = (location != null && location.has("country")) ? location.get("country").getAsString() : null;
            double lat = getDoubleSafe(location, "lat", Double.NaN);
            double lon = getDoubleSafe(location, "lon", Double.NaN);
            String tzId = (location != null && location.has("tz_id")) ? location.get("tz_id").getAsString() : null;
            String localtime = (location != null && location.has("localtime")) ? location.get("localtime").getAsString() : null;

            // air quality (pm2_5) - present under current.air_quality.pm2_5 on some plans
            double aqiPm25 = Double.NaN;
            if (current.has("air_quality") && current.get("air_quality").isJsonObject()) {
                JsonObject aq = current.getAsJsonObject("air_quality");
                if (aq.has("pm2_5")) {
                    try { aqiPm25 = aq.get("pm2_5").getAsDouble(); } catch (Exception ignored) {}
                }
            }

            WeatherData wd = new WeatherData(
                    tempC,
                    feels,
                    humidity < 0 ? 0 : humidity,
                    condText,
                    windKph,
                    pressureMb,
                    visKm,
                    uv,
                    cloud,
                    locName,
                    region,
                    country,
                    lat,
                    lon,
                    tzId,
                    localtime,
                    aqiPm25,
                    iconUrl
            );

            return wd;

        } catch (IOException | InterruptedException e) {
            // network issue -> wrap and throw
            throw new WeatherException("Network error while fetching current weather");
        } catch (WeatherException we) {
            throw we;
        } catch (Exception ex) {
            throw new WeatherException("Failed to parse current weather");
        }
    }

    @Override
    public List<ForecastDay> getForecast(String city) throws WeatherException {
        try {
            // request 7 days (was 5)
            String url = String.format("%s?key=%s&q=%s&days=7&aqi=yes&alerts=no", BASE_FORECAST, apiKey, encode(city));
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw parseApiError(resp.body());
            }

            JsonObject root = gson.fromJson(resp.body(), JsonObject.class);
            JsonObject forecastObj = root.has("forecast") && root.get("forecast").isJsonObject()
                    ? root.getAsJsonObject("forecast") : null;
            if (forecastObj == null || !forecastObj.has("forecastday")) {
                throw new WeatherException("Invalid API response (missing forecast)");
            }

            JsonArray days = forecastObj.getAsJsonArray("forecastday");
            List<ForecastDay> list = new ArrayList<>();

            for (JsonElement el : days) {
                if (!el.isJsonObject()) continue;
                JsonObject dayObj = el.getAsJsonObject();
                String dateStr = dayObj.has("date") ? dayObj.get("date").getAsString() : null;
                String label = (dateStr != null) ? shortDayLabel(dateStr) : "Day";

                JsonObject day = dayObj.has("day") && dayObj.get("day").isJsonObject() ? dayObj.getAsJsonObject("day") : null;
                JsonObject cond = (day != null && day.has("condition") && day.get("condition").isJsonObject())
                        ? day.getAsJsonObject("condition") : null;

                double min = (day != null) ? getDoubleSafe(day, "mintemp_c", Double.NaN) : Double.NaN;
                double max = (day != null) ? getDoubleSafe(day, "maxtemp_c", Double.NaN) : Double.NaN;
                int avgHumidity = (day != null) ? getIntSafe(day, "avghumidity", -1) : -1;
                int dailyChance = -1;
                if (day != null) {
                    if (day.has("daily_chance_of_rain")) dailyChance = getIntSafe(day, "daily_chance_of_rain", -1);
                    else if (day.has("daily_chance_of_snow")) dailyChance = getIntSafe(day, "daily_chance_of_snow", -1);
                }

                String condText = (cond != null && cond.has("text")) ? cond.get("text").getAsString() : null;
                String iconUrl = null;
                if (cond != null && cond.has("icon")) {
                    iconUrl = cond.get("icon").getAsString();
                    if (iconUrl != null && iconUrl.startsWith("//")) iconUrl = "https:" + iconUrl;
                }

                ForecastDay fd = new ForecastDay(label, min, max, condText);
                fd.setAvgHumidity(avgHumidity);
                fd.setChanceOfRain(dailyChance);
                fd.setIconUrl(iconUrl);

                // astro data
                if (dayObj.has("astro") && dayObj.get("astro").isJsonObject()) {
                    JsonObject astro = dayObj.getAsJsonObject("astro");
                    if (astro.has("sunrise")) fd.setSunrise(astro.get("sunrise").getAsString());
                    if (astro.has("sunset")) fd.setSunset(astro.get("sunset").getAsString());
                    if (astro.has("moon_phase")) fd.setMoonPhase(astro.get("moon_phase").getAsString());
                    if (astro.has("moon_illumination")) fd.setMoonIllumination(astro.get("moon_illumination").getAsString());
                }

                // hourly
                if (dayObj.has("hour") && dayObj.get("hour").isJsonArray()) {
                    JsonArray hours = dayObj.getAsJsonArray("hour");
                    List<HourlyWeather> hourly = new ArrayList<>();
                    for (JsonElement he : hours) {
                        if (!he.isJsonObject()) continue;
                        JsonObject ho = he.getAsJsonObject();
                        String time = ho.has("time") ? ho.get("time").getAsString() : null;
                        double temp = getDoubleSafe(ho, "temp_c", Double.NaN);
                        HourlyWeather hw = new HourlyWeather();
                        hw.setTime(time);
                        hw.setTempC(temp);
                        hw.setFeelsLikeC(getDoubleSafe(ho, "feelslike_c", Double.NaN));
                        hw.setHumidity(getIntSafe(ho, "humidity", -1));
                        hw.setWindKph(getDoubleSafe(ho, "wind_kph", Double.NaN));
                        hw.setPrecipMm(getDoubleSafe(ho, "precip_mm", Double.NaN));
                        hw.setChanceOfRain(getIntSafe(ho, "chance_of_rain", -1));
                        if (ho.has("condition") && ho.get("condition").isJsonObject()) {
                            JsonObject hcond = ho.getAsJsonObject("condition");
                            if (hcond.has("text")) hw.setCondition(hcond.get("text").getAsString());
                            if (hcond.has("icon")) {
                                String hi = hcond.get("icon").getAsString();
                                if (hi != null && hi.startsWith("//")) hi = "https:" + hi;
                                hw.setIconUrl(hi);
                            }
                        }
                        hourly.add(hw);
                    }
                    fd.setHourly(hourly);
                }

                list.add(fd);
            }

            return list;

        } catch (IOException | InterruptedException e) {
            throw new WeatherException("Network error while fetching forecast");
        } catch (WeatherException we) {
            throw we;
        } catch (Exception ex) {
            throw new WeatherException("Failed to parse forecast");
        }
    }

    // helper: parse API error JSON (weatherapi returns {"error":{"message":"..."} })
    private WeatherException parseApiError(String body) {
        try {
            JsonObject o = gson.fromJson(body, JsonObject.class);
            if (o != null && o.has("error") && o.getAsJsonObject("error").has("message")) {
                String msg = o.getAsJsonObject("error").get("message").getAsString();
                return new WeatherException("API error: " + msg);
            }
        } catch (Exception ignored) {}
        return new WeatherException("API returned error or invalid response");
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static double getDoubleSafe(JsonObject o, String name, double fallback) {
        try { if (o != null && o.has(name) && !o.get(name).isJsonNull()) return o.get(name).getAsDouble(); } catch (Exception ignored) {}
        return fallback;
    }

    private static int getIntSafe(JsonObject o, String name, int fallback) {
        try { if (o != null && o.has(name) && !o.get(name).isJsonNull()) return o.get(name).getAsInt(); } catch (Exception ignored) {}
        return fallback;
    }

    private static String shortDayLabel(String isoDate) {
        try {
            LocalDate ld = LocalDate.parse(isoDate);
            DayOfWeek dow = ld.getDayOfWeek();
            return dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH); // Mon, Tue...
        } catch (Exception e) {
            return isoDate;
        }
    }
}
