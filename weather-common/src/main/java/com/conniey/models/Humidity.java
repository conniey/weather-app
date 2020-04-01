package com.conniey.models;

/**
 * Measurement for specific humidity.
 */
public class Humidity {
    private final double massOfVapor;
    private final double volumeOfAir;
    private final double specificHumidity;

    /**
     * Creates a humidity measurement.
     *
     * @param massOfVapor Mass of water vapor in grams.
     * @param volumeOfAir Volume of air measured in cubic meters.
     */
    public Humidity(double massOfVapor, double volumeOfAir) {
        this.massOfVapor = massOfVapor;
        this.volumeOfAir = volumeOfAir;
        this.specificHumidity = massOfVapor / volumeOfAir;
    }

    /**
     * Gets the mass of water vapor in grams.
     *
     * @return the mass of water vapor in grams.
     */
    public double getMassOfVapor() {
        return massOfVapor;
    }

    /**
     * Gets the volume of air in cubic meters (m^3).
     *
     * @return the volume of air in cubic meters (m^3).
     */
    public double getVolumeOfAir() {
        return volumeOfAir;
    }

    /**
     * Gets the specific humidity in (g/m^3).
     *
     * @return the specific humidity in (g/m^3).
     */
    public double getSpecificHumidity() {
        return specificHumidity;
    }

    @Override
    public String toString() {
        return String.format("%,.2f g/m^3", specificHumidity);
    }

}
