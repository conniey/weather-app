package com.conniey.aggregator;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobServiceAsyncClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final ConcurrentHashMap<String, Aggregator> currentlyProcessing = new ConcurrentHashMap<>();
    private static final ObjectMapper serializer = new ObjectMapper();

    /**
     * Small program that would run on an IoT device and transmit measurements.
     *
     * @param args unused.
     */
    public static void main(String[] args) throws IOException {
        final String fullyQualifiedNamespace = "my-event-hubs-namespace.servicebus.windows.net";
        final String eventHubName = "event-hub-name";
        final TokenCredential credential = new DefaultAzureCredentialBuilder()
                .build();

        final BlobServiceAsyncClient blobServiceClient = new BlobServiceClientBuilder()
                .credential(credential)
                .buildAsyncClient();
        final BlobContainerAsyncClient blobContainer = blobServiceClient
                .getBlobContainerAsyncClient("telemetry-checkpoints");

        final BlobContainerClient statisticsContainer = new BlobContainerClientBuilder()
                .credential(credential)
                .containerName("statistics")
                .buildClient();
        CheckpointStore checkpointStore = new BlobCheckpointStore(blobContainer);
        EventProcessorClient processor = new EventProcessorClientBuilder()
                .credential(fullyQualifiedNamespace, eventHubName, credential)
                .consumerGroup("$DEFAULT")
                .checkpointStore(checkpointStore)
                .processEvent(context -> {
                    String partitionId = context.getPartitionContext().getPartitionId();
                    Aggregator aggregator = currentlyProcessing.computeIfAbsent(partitionId,
                            key -> createAggregator(partitionId, statisticsContainer));

                    aggregator.onEvent(context.getEventData());
                })
                .processError(context -> {
                    final String partitionId = context.getPartitionContext().getPartitionId();
                    System.err.printf("Closing processor. Id: %s. Error: %s%n", partitionId, context.getThrowable());
                    final Aggregator removed = currentlyProcessing.remove(partitionId);
                    if (removed != null) {
                        removed.close();
                    }
                })
                .processPartitionClose(context -> {
                    final String partitionId = context.getPartitionContext().getPartitionId();
                    System.err.printf("Closing existing processor. Id: %s. Close Reason: %s%n",
                            partitionId, context.getCloseReason());

                    final Aggregator removed = currentlyProcessing.remove(partitionId);
                    if (removed != null) {
                        removed.close();
                    }
                })
                .buildEventProcessorClient();

        processor.start();

        System.out.println("Processing telemetry. Press any key to exit.");
        System.in.read();
        System.out.println("Stopping");

        processor.stop();
    }

    private static Aggregator createAggregator(String partitionId, BlobContainerClient containerClient) {
        switch (partitionId) {
            case "0":
                return new TemperatureAggregator(serializer, containerClient);
            case "1":
                throw new RuntimeException("Not implemented.");
            case "2":
                throw new RuntimeException("Not implemented for CO2.");
            default:
                throw new IllegalArgumentException("Partition id is not recognized: " + partitionId);
        }
    }
}
