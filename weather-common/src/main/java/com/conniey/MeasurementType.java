package com.conniey;

public enum MeasurementType {
    TEMPERATURE("temperature"),
    HUMIDITY("humidity"),
    CO2("CO2");

    private final String value;

    MeasurementType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
