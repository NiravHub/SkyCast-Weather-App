package oep.skycast.service;

import oep.skycast.model.WeatherData;
import oep.skycast.model.ForecastDay;
import oep.skycast.exceptions.WeatherException;
import java.util.List;

public interface WeatherProvider {

    WeatherData getCurrentWeather(String city) throws WeatherException;

    List<ForecastDay> getForecast(String city) throws WeatherException;
}
