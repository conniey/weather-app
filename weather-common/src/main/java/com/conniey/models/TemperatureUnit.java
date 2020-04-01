package com.conniey.models;

/**
 * Units for temperature measurement.
 */
public enum TemperatureUnit {
    CELSIUS("C"),
    KELVIN("K"),
    FAHRENHEIT("F");

    private final String value;

    TemperatureUnit(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
