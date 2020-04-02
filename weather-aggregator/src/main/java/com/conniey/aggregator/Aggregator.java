package com.conniey.aggregator;

import com.azure.messaging.eventhubs.models.EventContext;

import java.time.Instant;

public interface Aggregator extends AutoCloseable {
    double getMinimum();

    double getMaximum();

    double getAverage();

    Instant getLastReported();

    void onEvent(EventContext event);

    @Override
    void close();
}
