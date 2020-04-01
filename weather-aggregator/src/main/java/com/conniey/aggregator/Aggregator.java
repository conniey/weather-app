package com.conniey.aggregator;

import com.azure.messaging.eventhubs.EventData;

import java.time.Instant;

public interface Aggregator extends AutoCloseable {
    double getMinimum();

    double getMaximum();

    double getAverage();

    Instant getLastReported();

    void onEvent(EventData event);
}
