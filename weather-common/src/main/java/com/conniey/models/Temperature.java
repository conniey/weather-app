package com.conniey.models;

/**
 * Represents a temperature measurement.
 */
public class Temperature {
    private final double temperature;

    private final TemperatureUnit unit;

    public Temperature(double temperature, TemperatureUnit unit) {
        this.temperature = temperature;
        this.unit = unit;
    }

    public double getTemperature() {
        return temperature;
    }

    public TemperatureUnit getUnit() {
        return unit;
    }

    @Override
    public String toString() {
        return temperature + " " + unit.getValue();
    }
}
