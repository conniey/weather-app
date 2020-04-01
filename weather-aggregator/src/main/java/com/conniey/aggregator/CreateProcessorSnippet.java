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
import com.azure.storage.blob.BlobContainerClientBuilder;

public class CreateProcessorSnippet {
    /**
     * Snippet shows how a processor is created.
     */
    public void process() throws Exception {
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

        processor.start();

        System.out.println("Processing telemetry. Press any key to exit.");
        System.in.read();
        System.out.println("Stopping");

        processor.stop();
    }

    private void onPartitionClose(CloseContext closeContext) {

    }

    private void onError(ErrorContext errorContext) {

    }

    private void onReceivedEvent(EventContext eventContext) {
    }
}
