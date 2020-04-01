package com.conniey.reporter;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.azure.messaging.eventhubs.models.CreateBatchOptions;
import com.conniey.models.Temperature;
import com.conniey.models.TemperatureUnit;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Random;

class SendTemperatureSnippet {
    private void sendTemperature(String fullyQualifiedNamespace, String eventHubName) throws Exception {
        Random random = new Random();
        ObjectMapper serializer = new ObjectMapper();
        TokenCredential credential = new DefaultAzureCredentialBuilder()
                .build();

        // Create a client that can publish messages to an Event Hub.
        EventHubProducerClient sender = new EventHubClientBuilder()
                .credential(fullyQualifiedNamespace, eventHubName, credential)
                .buildProducerClient();

        // I want all of my temperature information to go to partition "0".
        // Humidity and CO2 measurements go to partition "1" and "2", respectively.
        CreateBatchOptions batchOptions = new CreateBatchOptions()
                .setPartitionId("0");

        EventDataBatch currentBatch = sender.createBatch(batchOptions);

        // For the sake of this demo, I have a for-loop. In a IoT device, this
        // may be an event loop that runs on a timer.
        for (int i = 0; i < 100; i++) {
            double temp = random.nextDouble() * 100.00;
            Temperature measurement = new Temperature(temp, TemperatureUnit.CELSIUS);
            String json = serializer.writeValueAsString(measurement);

            // Events contain an opaque series of bytes. To understand what these
            // bytes represent in my aggregator, I add some application properties such as
            // "measurement-type" and "content-type".
            EventData eventData = new EventData(json.getBytes());
            eventData.getProperties().put("measurement-type", "temperature");
            eventData.getProperties().put("content-type", "json");

            // If the event does not fit in a batch, that means the batch is full, so
            // we send it and create another batch.
            if (!currentBatch.tryAdd(eventData)) {
                sender.send(currentBatch);
                currentBatch = sender.createBatch(batchOptions);

                // Add the one that did not fit.
                currentBatch.tryAdd(eventData);
            }
        }
    }
}
