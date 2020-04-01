package com.conniey.aggregator;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class Main {
    /**
     * Small program that would run on an IoT device and transmit measurements.
     *
     * @param args unused.
     */
    public static void main(String[] args) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();

        final String fullyQualifiedNamespace = "my-event-hubs-namespace.servicebus.windows.net";
        final String eventHubName = "event-hub-name";
        final TokenCredential credential = new DefaultAzureCredentialBuilder()
                .build();

        final BlobServiceAsyncClient blobServiceClient = new BlobServiceClientBuilder()
                .credential(credential)
                .buildAsyncClient();
        final BlobContainerAsyncClient blobContainer = blobServiceClient
                .getBlobContainerAsyncClient("telemetry-checkpoints");

        CheckpointStore checkpointStore = new BlobCheckpointStore(blobContainer);
        EventProcessorClient processor = new EventProcessorClientBuilder()
                .credential(fullyQualifiedNamespace, eventHubName, credential)
                .consumerGroup("$DEFAULT")
                .processEvent(context -> {
                    String partitionId = context.getPartitionContext().getPartitionId();
                })
                .processPartitionInitialization(context -> {

                })
                .processError(context -> {

                })
                .processPartitionClose(context -> {

                })
                .checkpointStore(checkpointStore)
                .buildEventProcessorClient();

        processor.start();

        System.out.println("Processing telemetry. Press any key to exit.");
        System.in.read();
        System.out.println("Stopping");

        processor.stop();
    }
}
