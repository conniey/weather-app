package com.conniey.aggregator;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.conniey.models.Temperature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Processes temperature events as they come from Event Hub
 */
public class TemperatureAggregator implements Aggregator {
    static final int NUMBER_TO_RETAIN = 20;

    private static final int CHECKPOINT_NUMBER = 10;
    private static final String BLOB_NAME = "temperature";

    private final AtomicReference<EventContext> lastEvent = new AtomicReference<>();
    private final AtomicBoolean isDisposed = new AtomicBoolean();
    private final ObjectMapper serializer;
    private final Double[] lastMeasurements;
    private final BlobClient blobClient;
    private int currentIndex = 0;

    private Instant lastReported = Instant.EPOCH;
    private double minTemperature = Double.MAX_VALUE;
    private double maxTemperature = Double.MIN_VALUE;
    private long numberProcessed = 0;

    public TemperatureAggregator(BlobContainerClient containerClient, ObjectMapper serializer) {
        this.serializer = serializer;
        this.blobClient = containerClient.getBlobClient(BLOB_NAME);

        if (blobClient.exists()) {
            try (PipedOutputStream outputStream = new PipedOutputStream();
                 PipedInputStream inputStream = new PipedInputStream(outputStream)) {
                this.blobClient.download(outputStream);

                Statistics statistics = serializer.readValue(inputStream, Statistics.class);
                minTemperature = statistics.getMinimum();
                maxTemperature = statistics.getMaximum();
                lastMeasurements = statistics.getLastMeasurements();
            } catch (IOException e) {
                throw new RuntimeException("Could not create output stream. Error: " + e);
            }
        } else {
            lastMeasurements = new Double[NUMBER_TO_RETAIN];
        }
    }

    /**
     * Gets the minimum temperature.
     *
     * @return the minimum temperature.
     */
    @Override
    public double getMinimum() {
        return minTemperature;
    }

    /**
     * Gets the maximum temperature.
     *
     * @return The maximum temperature.
     */
    @Override
    public double getMaximum() {
        return maxTemperature;
    }

    /**
     * Gets the average of the last {@link #NUMBER_TO_RETAIN} measurements.
     *
     * @return The average of the last {@link #NUMBER_TO_RETAIN} measurements.
     */
    @Override
    public double getAverage() {
        return Arrays.stream(lastMeasurements)
                .mapToDouble(e -> e)
                .average()
                .orElse(Double.MIN_VALUE);
    }

    @Override
    public Instant getLastReported() {
        return lastReported;
    }

    @Override
    public void onEvent(EventContext context) {
        final EventData data = context.getEventData();
        final Instant enqueuedTime = data.getEnqueuedTime();
        if (enqueuedTime.isAfter(lastReported)) {
            lastReported = enqueuedTime;
        }

        final byte[] body = data.getBody();

        final Temperature temperature;
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

        int index = currentIndex % lastMeasurements.length;
        lastMeasurements[index] = value;

        currentIndex++;

        lastEvent.set(context);

        long processed = numberProcessed++;
        if ((processed % CHECKPOINT_NUMBER) == 0) {
            context.updateCheckpoint();
        }
    }

    @Override
    public void close() {
        if (isDisposed.getAndSet(true)) {
            return;
        }

        final EventContext lastEventContext = lastEvent.get();
        if (lastEventContext != null) {
            lastEventContext.updateCheckpoint();
        }

        // persist current aggregated information to blob storage so another
        // processor can take over.
        final Statistics statistics = new Statistics(minTemperature, maxTemperature,
                Arrays.copyOf(lastMeasurements, lastMeasurements.length), lastReported);

        final String json;
        try {
            json = serializer.writeValueAsString(statistics);
        } catch (JsonProcessingException e) {
            System.err.println("Error occurred serializing data: " + e);
            return;
        }

        final byte[] contents = json.getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream input = new ByteArrayInputStream(contents)) {
            blobClient.upload(input, contents.length);
        } catch (IOException e) {
            System.err.println("Could not create input stream to persist stats. Error: " + e);
        }
    }
}
