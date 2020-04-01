package com.conniey.aggregator;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.messaging.eventhubs.models.CloseContext;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.ConcurrentHashMap;

public class ProcessingSnippet {
    private final ConcurrentHashMap<String, Aggregator> currentlyProcessing = new ConcurrentHashMap<>();
    private final ObjectMapper serializer = new ObjectMapper();
    private final BlobContainerClient statisticsContainer;

    public ProcessingSnippet() {
        statisticsContainer = new BlobContainerClientBuilder()
                .credential(new DefaultAzureCredentialBuilder().build())
                .containerName("statistics")
                .buildClient();
    }

    /**
     * Snippet shows how a processor is created.
     */
    public void createProcessor() {
        String fullyQualifiedNamespace = "my-event-hubs-namespace.servicebus.windows.net";
        String eventHubName = "event-hub-name";
        TokenCredential credential = new DefaultAzureCredentialBuilder()
                .build();

        BlobContainerAsyncClient blobContainer = new BlobContainerClientBuilder()
                .credential(credential)
                .containerName("telemetry-checkpoints")
                .buildAsyncClient();

        CheckpointStore checkpointStore = new BlobCheckpointStore(blobContainer);
        EventProcessorClient processor = new EventProcessorClientBuilder()
                .credential(fullyQualifiedNamespace, eventHubName, credential)
                .consumerGroup("$DEFAULT")
                .checkpointStore(checkpointStore)
                .processEvent(eventContext -> onReceivedEvent(eventContext))
                .processError(errorContext -> onError(errorContext))
                .processPartitionClose(closeContext -> onPartitionClose(closeContext))
                .buildEventProcessorClient();
    }

    private void onPartitionClose(CloseContext context) {
        final String partitionId = context.getPartitionContext().getPartitionId();
        System.err.printf("Closing existing processor. Id: %s. Close Reason: %s%n",
                partitionId, context.getCloseReason());

        final Aggregator removed = currentlyProcessing.remove(partitionId);
        if (removed != null) {
            removed.close();
        }
    }

    private void onReceivedEvent(EventContext context) {
        final String partitionId = context.getPartitionContext().getPartitionId();
        final Aggregator aggregator = currentlyProcessing.computeIfAbsent(partitionId,
                key -> createAggregator(partitionId, statisticsContainer));

        aggregator.onEvent(context.getEventData());
    }

    private void onError(ErrorContext context) {
        final String partitionId = context.getPartitionContext().getPartitionId();
        System.err.printf("Closing processor. Id: %s. Error: %s%n", partitionId, context.getThrowable());
        final Aggregator removed = currentlyProcessing.remove(partitionId);
        if (removed != null) {
            removed.close();
        }
    }

    private Aggregator createAggregator(String partitionId, BlobContainerClient containerClient) {
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
