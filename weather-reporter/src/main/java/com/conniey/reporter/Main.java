package com.conniey.reporter;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Small program that would run on an IoT device and transmit measurements.
 */
public class Main {
    /**
     * Starts transmitting measurements to Event Hubs.
     *
     * @param args Unused arguments.
     */
    public static void main(String[] args) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();

        final String fullyQualifiedNamespace = "my-event-hubs-namespace.servicebus.windows.net";
        final String eventHubName = "event-hub-name";
        final TokenCredential credential = new DefaultAzureCredentialBuilder()
                .build();
        final EventHubProducerAsyncClient sender =  new EventHubClientBuilder()
                .credential(fullyQualifiedNamespace, eventHubName, credential)
                .buildAsyncProducerClient();

        final Reporter reporter = new Reporter(sender, mapper);
        final Device device = new Device(reporter);

        device.start();
        System.out.println("Transmitting telemetry. Press any key to exit.");
        int read = System.in.read();
        System.out.println("Stopping");

        device.close();
        reporter.close();
    }
}
