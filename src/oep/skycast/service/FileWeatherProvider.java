package oep.skycast.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import oep.skycast.model.WeatherData;
import oep.skycast.model.ForecastDay;
import oep.skycast.exceptions.WeatherException;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileWeatherProvider implements WeatherProvider {

    private final String filePath;

    public FileWeatherProvider(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public WeatherData getCurrentWeather(String city) throws WeatherException {
        try {
            JsonObject json = JsonParser.parseReader(new FileReader(filePath)).getAsJsonObject();
            JsonObject current = json.getAsJsonObject("current");

            return new WeatherData(
                current.get("temperature").getAsDouble(),
                current.get("feelsLike").getAsDouble(),
                current.get("humidity").getAsInt(),
                current.get("condition").getAsString(),
                current.get("windSpeed").getAsDouble()
            );

        } catch (IOException e) {
            throw new WeatherException("Cannot read weather file!");
        }
    }

    @Override
    public List<ForecastDay> getForecast(String city) throws WeatherException {
        try {
            JsonObject json = JsonParser.parseReader(new FileReader(filePath)).getAsJsonObject();
            var list = new ArrayList<ForecastDay>();

            for (var element : json.getAsJsonArray("forecast")) {
                JsonObject dayObj = element.getAsJsonObject();
                list.add(new ForecastDay(
                    dayObj.get("day").getAsString(),
                    dayObj.get("minTemp").getAsDouble(),
                    dayObj.get("maxTemp").getAsDouble(),
                    dayObj.get("condition").getAsString()
                ));
            }
            return list;

        } catch (IOException e) {
            throw new WeatherException("Cannot load forecast data!");
        }
    }
}
