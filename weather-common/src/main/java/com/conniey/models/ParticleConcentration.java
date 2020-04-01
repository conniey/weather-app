package com.conniey.models;

import java.util.Objects;

/**
 * Represents the concentration of a compound measured. (ie. Concentration of CO2 is 300ppm)
 */
public class ParticleConcentration {
    private final String particleMeasured;
    private final double partsPerMillion;

    public ParticleConcentration(String particleMeasured, double partsPerMillion) {
        this.particleMeasured = Objects.requireNonNull(particleMeasured, "'particleMeasured' cannot be null.");
        this.partsPerMillion = partsPerMillion;
    }

    public String getParticleMeasured() {
        return particleMeasured;
    }

    public  double getPartsPerMillion() {
        return partsPerMillion;
    }

    @Override
    public String toString() {
        return particleMeasured + " " + partsPerMillion + "ppm";
    }
}
