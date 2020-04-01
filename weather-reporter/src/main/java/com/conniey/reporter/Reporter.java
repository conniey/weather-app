package com.conniey.reporter;

import com.azure.core.credential.TokenCredential;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
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
class Reporter implements AutoCloseable {
    private final AtomicReference<EventDataBatch> currentTemperatureBatch;
    private final AtomicReference<EventDataBatch> currentHumidityBatch;
    private final AtomicReference<EventDataBatch> currentCO2Batch;

    private final CreateBatchOptions temperatureOptions;
    private final CreateBatchOptions humidityOptions;
    private final CreateBatchOptions co2Options;

    private final EventHubProducerAsyncClient sender;
    private final ObjectMapper serializer;
    private final AtomicBoolean isDisposed = new AtomicBoolean();

    Reporter(String eventHubsNamespace, String eventHubName, TokenCredential credential, ObjectMapper serializer) {
        this.serializer = serializer;
        this.sender = new EventHubClientBuilder()
                .credential(eventHubsNamespace, eventHubName, credential)
                .buildAsyncProducerClient();

        this.temperatureOptions = new CreateBatchOptions().setPartitionId("0");
        this.humidityOptions = new CreateBatchOptions().setPartitionId("1");
        this.co2Options = new CreateBatchOptions().setPartitionId("2");
        this.currentTemperatureBatch = new AtomicReference<>(sender.createBatch(temperatureOptions).block());
        this.currentHumidityBatch = new AtomicReference<>(sender.createBatch(humidityOptions).block());
        this.currentCO2Batch = new AtomicReference<>(sender.createBatch(co2Options).block());
    }

    Mono<Void> report(Temperature temperature) {
        return getJson(temperature).map(json -> {
            final EventData event = new EventData(json.getBytes(StandardCharsets.UTF_8));
            event.getProperties().put(MEASUREMENT_TYPE, MeasurementType.TEMPERATURE.getValue());
            event.getProperties().put(CONTENT_TYPE, JSON);

            return event;
        }).flatMap(event -> addToBatch(event, currentTemperatureBatch, temperatureOptions));
    }

    Mono<Void> report(Humidity humidity) {
        return getJson(humidity).map(json -> {
            final EventData event = new EventData(json.getBytes(StandardCharsets.UTF_8));
            event.getProperties().put(MEASUREMENT_TYPE, MeasurementType.HUMIDITY.getValue());
            event.getProperties().put(CONTENT_TYPE, JSON);

            return event;
        }).flatMap(event -> addToBatch(event, currentHumidityBatch, humidityOptions));
    }

    Mono<Void> report(ParticleConcentration co2) {
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

        return sender.createBatch(batchOptions).map(newBatch -> {
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
                throw Exceptions.propagate(new RuntimeException("Unable to serialize temperature value.", e));
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
