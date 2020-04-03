package com.conniey.reporter;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.models.CreateBatchOptions;
import com.conniey.MeasurementType;
import com.conniey.models.Humidity;
import com.conniey.models.ParticleConcentration;
import com.conniey.models.Temperature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.conniey.Constants.CONTENT_TYPE;
import static com.conniey.Constants.JSON;
import static com.conniey.Constants.MEASUREMENT_TYPE;

/**
 * Sends telemetry information to Event Hubs.
 */
public class Reporter implements AutoCloseable {
    private final AtomicReference<EventDataBatch> currentTemperatureBatch;
    private final AtomicReference<EventDataBatch> currentHumidityBatch;
    private final AtomicReference<EventDataBatch> currentCO2Batch;

    private final CreateBatchOptions temperatureOptions;
    private final CreateBatchOptions humidityOptions;
    private final CreateBatchOptions co2Options;

    private final EventHubProducerAsyncClient sender;
    private final ObjectMapper serializer;
    private final AtomicBoolean isDisposed = new AtomicBoolean();

    /**
     * Creates a reporter that produces events using the sender.
     *
     * @param sender Sends events to an Event Hub.
     * @param serializer Json serializer to serialize the data into.
     */
    public Reporter(EventHubProducerAsyncClient sender, ObjectMapper serializer) {
        this.serializer = serializer;
        this.sender = sender;

        this.temperatureOptions = new CreateBatchOptions()
                .setPartitionId("0");
        this.humidityOptions = new CreateBatchOptions()
                .setPartitionId("1");
        this.co2Options = new CreateBatchOptions()
                .setPartitionId("2");

        this.currentTemperatureBatch = new AtomicReference<>(sender.createBatch(temperatureOptions).block());
        this.currentHumidityBatch = new AtomicReference<>(sender.createBatch(humidityOptions).block());
        this.currentCO2Batch = new AtomicReference<>(sender.createBatch(co2Options).block());
    }

    /**
     * Adds temperature information to the current batch of events and sends the batch if it is full.
     *
     * @param temperature Temperature to add.
     *
     * @return Mono that completes when the event has been successfully added to the batch or when the batch has been
     *         sent to the service.
     * @throws IllegalArgumentException if {@code temperature} cannot be serialized into json. Or the event
     *         created from this data is too large to fit in an empty batch.
     */
    public Mono<Void> report(Temperature temperature) {
        return getJson(temperature).map(json -> {
            final EventData event = new EventData(json.getBytes(StandardCharsets.UTF_8));
            event.getProperties().put(MEASUREMENT_TYPE, MeasurementType.TEMPERATURE.getValue());
            event.getProperties().put(CONTENT_TYPE, JSON);

            return event;
        }).flatMap(event -> addToBatch(event, currentTemperatureBatch, temperatureOptions));
    }

    /**
     * Adds humidity measurement to the current batch of events and sends the batch if it is full.
     *
     * @param humidity Humidity to add.
     *
     * @return Mono that completes when the event has been successfully added to the batch or when the batch has been
     *         sent to the service.
     * @throws IllegalArgumentException if {@code humidity} cannot be serialized into json. Or the event created
     *         from this data is too large to fit in an empty batch.
     */
    public Mono<Void> report(Humidity humidity) {
        return getJson(humidity).map(json -> {
            final EventData event = new EventData(json.getBytes(StandardCharsets.UTF_8));
            event.getProperties().put(MEASUREMENT_TYPE, MeasurementType.HUMIDITY.getValue());
            event.getProperties().put(CONTENT_TYPE, JSON);

            return event;
        }).flatMap(event -> addToBatch(event, currentHumidityBatch, humidityOptions));
    }

    /**
     * Adds CO2 measurement to the current batch of events and sends the batch if it is full.
     *
     * @param co2 CO2 measurement.
     *
     * @return Mono that completes when the event has been successfully added to the batch or when the batch has been
     *         sent to the service.
     * @throws IllegalArgumentException if {@code co2} cannot be serialized into json. Or the event created from
     *         this data is too large to fit in an empty batch.
     */
    public Mono<Void> report(ParticleConcentration co2) {
        return getJson(co2).map(json -> {
            final EventData event = new EventData(json.getBytes(StandardCharsets.UTF_8));
            event.getProperties().put(MEASUREMENT_TYPE, MeasurementType.CO2.getValue());
            event.getProperties().put(CONTENT_TYPE, JSON);

            return event;
        }).flatMap(event -> addToBatch(event, currentCO2Batch, co2Options));
    }

    private Mono<Void> addToBatch(EventData event, AtomicReference<EventDataBatch> batchReference,
            CreateBatchOptions batchOptions) {

        final EventDataBatch currentBatch = batchReference.get();
        if (currentBatch.tryAdd(event)) {
            return Mono.empty();
        }

        // The batch was full, so we send the existing batch, then create a new
        // batch to add the event to it.
        return sender.send(currentBatch).flatMap(unused -> sender.createBatch(batchOptions))
                .map(newBatch -> {
                    final EventDataBatch batchToUse;
                    if (batchReference.compareAndSet(currentBatch, newBatch)) {
                        batchToUse = newBatch;
                    } else {
                        batchToUse = batchReference.get();
                    }

                    if (!batchToUse.tryAdd(event)) {
                        throw Exceptions.propagate(
                                new IllegalArgumentException("Event cannot fit in a single event. " + event));
                    }

                    return newBatch;
                }).then();
    }

    private <T> Mono<String> getJson(T object) {
        return Mono.fromCallable(() -> {
            try {
                return serializer.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw Exceptions.propagate(new IllegalArgumentException("Unable to serialize temperature value.", e));
            }
        });
    }

    @Override
    public void close() {
        if (isDisposed.getAndSet(true)) {
            return;
        }

        sender.close();
    }
}
