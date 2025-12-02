package oep.skycast.model;

/**
 * WeatherData - model to hold current weather + location + air quality info.
 *
 * Fields included:
 *  - temperature, feelsLike, humidity, condition, windSpeed
 *  - pressureMb, visibilityKm, uv, cloud
 *  - locationName, region, country, latitude, longitude, tzId, localTime
 *  - aqiPm25 (PM2.5)
 *  - iconUrl (full https URL if available)
 */
public class WeatherData {

    // --- basic current weather ---
    private double temperature;    // °C
    private double feelsLike;      // °C
    private int humidity;          // %
    private String condition;      // text description
    private double windSpeed;      // km/h

    // --- optional extra current fields ---
    private double pressureMb;     // hPa / millibars
    private double visibilityKm;   // km
    private double uv;             // UV index
    private int cloud;             // cloud percentage

    // --- location / timezone ---
    private String locationName;
    private String region;
    private String country;
    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private String tzId;
    private String localTime; // e.g., "2025-11-28 19:42"

    // --- air quality ---
    private double aqiPm25 = Double.NaN; // PM2.5 µg/m³

    // --- icons / presentation ---
    private String iconUrl;

    // --- constructors ---

    public WeatherData() {}

    /**
     * Backwards-compatible constructor (older code might use this).
     */
    public WeatherData(double temperature, double feelsLike, int humidity, String condition, double windSpeed) {
        this.temperature = temperature;
        this.feelsLike = feelsLike;
        this.humidity = humidity;
        this.condition = condition;
        this.windSpeed = windSpeed;
    }

    /**
     * Full constructor including optional fields.
     */
    public WeatherData(double temperature,
                       double feelsLike,
                       int humidity,
                       String condition,
                       double windSpeed,
                       double pressureMb,
                       double visibilityKm,
                       double uv,
                       int cloud,
                       String locationName,
                       String region,
                       String country,
                       double latitude,
                       double longitude,
                       String tzId,
                       String localTime,
                       double aqiPm25,
                       String iconUrl) {
        this.temperature = temperature;
        this.feelsLike = feelsLike;
        this.humidity = humidity;
        this.condition = condition;
        this.windSpeed = windSpeed;
        this.pressureMb = pressureMb;
        this.visibilityKm = visibilityKm;
        this.uv = uv;
        this.cloud = cloud;
        this.locationName = locationName;
        this.region = region;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.tzId = tzId;
        this.localTime = localTime;
        this.aqiPm25 = aqiPm25;
        this.iconUrl = iconUrl;
    }

    // --- getters & setters ---

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getFeelsLike() { return feelsLike; }
    public void setFeelsLike(double feelsLike) { this.feelsLike = feelsLike; }

    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }

    public double getPressureMb() { return pressureMb; }
    public void setPressureMb(double pressureMb) { this.pressureMb = pressureMb; }

    public double getVisibilityKm() { return visibilityKm; }
    public void setVisibilityKm(double visibilityKm) { this.visibilityKm = visibilityKm; }

    public double getUv() { return uv; }
    public void setUv(double uv) { this.uv = uv; }

    public int getCloud() { return cloud; }
    public void setCloud(int cloud) { this.cloud = cloud; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getTzId() { return tzId; }
    public void setTzId(String tzId) { this.tzId = tzId; }

    public String getLocalTime() { return localTime; }
    public void setLocalTime(String localTime) { this.localTime = localTime; }

    public double getAqiPm25() { return aqiPm25; }
    public void setAqiPm25(double aqiPm25) { this.aqiPm25 = aqiPm25; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    @Override
    public String toString() {
        return "WeatherData{" +
                "temperature=" + temperature +
                ", feelsLike=" + feelsLike +
                ", humidity=" + humidity +
                ", condition='" + condition + '\'' +
                ", windSpeed=" + windSpeed +
                ", pressureMb=" + pressureMb +
                ", visibilityKm=" + visibilityKm +
                ", uv=" + uv +
                ", cloud=" + cloud +
                ", locationName='" + locationName + '\'' +
                ", region='" + region + '\'' +
                ", country='" + country + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", tzId='" + tzId + '\'' +
                ", localTime='" + localTime + '\'' +
                ", aqiPm25=" + aqiPm25 +
                ", iconUrl='" + iconUrl + '\'' +
                '}';
    }
}
