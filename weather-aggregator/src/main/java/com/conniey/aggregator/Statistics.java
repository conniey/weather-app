package com.conniey.aggregator;

import java.time.Instant;

/**
 * Represents a set of information for measurements received.
 */
public class Statistics {
    private final double minimum;
    private final double maximum;
    private final Double[] lastMeasurements;
    private final Instant lastReported;

    public Statistics(double minimum, double maximum, Double[] lastMeasurements, Instant lastReported) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.lastMeasurements = lastMeasurements;
        this.lastReported = lastReported;
    }

    public double getMinimum() {
        return minimum;
    }

    public double getMaximum() {
        return maximum;
    }

    public Double[] getLastMeasurements() {
        return lastMeasurements;
    }

    public Instant getLastReported() {
        return lastReported;
    }
}
