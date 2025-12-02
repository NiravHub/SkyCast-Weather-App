package oep.skycast.exceptions;

/**
 * Simple WeatherException with single-string constructor.
 */
public class WeatherException extends Exception {
    public WeatherException(String message) { super(message); }
}
