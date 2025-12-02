package oep.skycast.model;

/**
 * Simple hourly weather model used for the 24-hour chart / details.
 */
public class HourlyWeather {
    private String time;    // e.g. "2025-11-28 14:00"
    private double tempC;
    private double feelsLikeC;
    private int humidity;
    private double windKph;
    private String condition;
    private String iconUrl;
    private double precipMm;
    private int chanceOfRain;

    public HourlyWeather() {}

    public HourlyWeather(String time, double tempC) {
        this.time = time;
        this.tempC = tempC;
    }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public double getTempC() { return tempC; }
    public void setTempC(double tempC) { this.tempC = tempC; }

    public double getFeelsLikeC() { return feelsLikeC; }
    public void setFeelsLikeC(double feelsLikeC) { this.feelsLikeC = feelsLikeC; }

    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }

    public double getWindKph() { return windKph; }
    public void setWindKph(double windKph) { this.windKph = windKph; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public double getPrecipMm() { return precipMm; }
    public void setPrecipMm(double precipMm) { this.precipMm = precipMm; }

    public int getChanceOfRain() { return chanceOfRain; }
    public void setChanceOfRain(int chanceOfRain) { this.chanceOfRain = chanceOfRain; }

    @Override
    public String toString() {
        return (time == null ? "" : (time + " ")) + String.format("%.1f°C", tempC) + (condition == null ? "" : " - " + condition);
    }
}
