package com.conniey.aggregator;

import com.azure.messaging.eventhubs.models.*;
import com.conniey.models.Temperature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public class TemperatureProcessingSnippet {
    private final ObjectMapper serializer = new ObjectMapper();
    private final AtomicReference<EventContext> lastEvent = new AtomicReference<>();

    private Instant lastReported = Instant.EPOCH;
    private double minTemperature = Double.MAX_VALUE;
    private double maxTemperature = Double.MIN_VALUE;
    private long numberProcessed = 0;

    /**
     * For each temperature event we receive, we extract data from it and
     * checkpoint every 50 messages.
     */
    public void onTemperatureEvent(EventContext context) {
        Instant enqueuedTime = context.getEventData().getEnqueuedTime();

        if (enqueuedTime.isAfter(lastReported)) {
            lastReported = enqueuedTime;
        }

        byte[] body = context.getEventData().getBody();

        Temperature temperature;
        try {
            temperature = serializer.readValue(body, 0, body.length, Temperature.class);
        } catch (IOException e) {
            System.err.printf("Unable to deserialize value: %s. Error: %s%n",
                    new String(body, StandardCharsets.UTF_8), e);
            return;
        }

        double value = temperature.getTemperature();
        if (value < minTemperature) {
            minTemperature = value;
        }

        if (value > maxTemperature) {
            maxTemperature = value;
        }

        lastEvent.set(context);

        long processed = numberProcessed++;
        if ((processed % 50) == 0) {
            context.updateCheckpoint();
        }
    }

    /**
     * Partitions can be lost because the application was shut down or the partition was stolen.
     * If the partition was stolen, it means another processor instance is going to start
     * reading messages from this partition.
     */
    public void onPartitionClose(CloseContext closeContext) {
        System.out.println("Partition has been lost: " + closeContext.getCloseReason());

        // Check the last context and if we haven't checkpointed it, then do so.
        EventContext lastEventContext = lastEvent.get();
        if (lastEventContext != null && (numberProcessed % 50) != 0) {
            lastEventContext.updateCheckpoint();
        }

        // Persist current aggregated information to blob storage so
        // another processor pick up from where we stopped aggregating data.
        Statistics statistics = new Statistics(minTemperature, maxTemperature, lastReported);

        // Serialize the `statistics` object and persist it in a durable store
        // like Azure Blob Storage.
    }
}
