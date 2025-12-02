package oep.skycast.model;

import java.util.ArrayList;
import java.util.List;

public class ForecastDay {
    private String day;
    private double minTemp;
    private double maxTemp;
    private String condition;

    // astro fields
    private String sunrise;      // "07:14 AM"
    private String sunset;       // "04:52 PM"
    private String moonPhase;    // "Waxing Gibbous" etc.
    private String moonIllumination; // percent if available

    // hourly breakdown for 24 hours
    private List<HourlyWeather> hourly = new ArrayList<>();

    // optional extras
    private int avgHumidity = -1;
    private int chanceOfRain = -1;
    private String iconUrl;

    public ForecastDay() {}

    public ForecastDay(String day, double minTemp, double maxTemp, String condition) {
        this.day = day;
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
        this.condition = condition;
    }

    // getters/setters for new fields
    public String getSunrise() { return sunrise; }
    public void setSunrise(String sunrise) { this.sunrise = sunrise; }

    public String getSunset() { return sunset; }
    public void setSunset(String sunset) { this.sunset = sunset; }

    public String getMoonPhase() { return moonPhase; }
    public void setMoonPhase(String moonPhase) { this.moonPhase = moonPhase; }

    public String getMoonIllumination() { return moonIllumination; }
    public void setMoonIllumination(String moonIllumination) { this.moonIllumination = moonIllumination; }

    public List<HourlyWeather> getHourly() { return hourly; }
    public void setHourly(List<HourlyWeather> hourly) { this.hourly = hourly; }

    // existing getters/setters...
    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }
    public double getMinTemp() { return minTemp; }
    public void setMinTemp(double minTemp) { this.minTemp = minTemp; }
    public double getMaxTemp() { return maxTemp; }
    public void setMaxTemp(double maxTemp) { this.maxTemp = maxTemp; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public int getAvgHumidity() { return avgHumidity; }
    public void setAvgHumidity(int avgHumidity) { this.avgHumidity = avgHumidity; }

    public int getChanceOfRain() { return chanceOfRain; }
    public void setChanceOfRain(int chanceOfRain) { this.chanceOfRain = chanceOfRain; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    @Override
    public String toString() {
        return day + ": " + minTemp + "°C - " + maxTemp + "°C (" + condition + ")";
    }
}
