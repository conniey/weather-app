package com.conniey;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.conniey.models.Humidity;
import com.conniey.models.ParticleConcentration;
import com.conniey.models.Temperature;
import com.conniey.models.TemperatureUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;

public class Main {
    /**
     * Small program that would run on an IoT device and transmit measurements.
     *
     * @param args unused.
     */
    public static void main(String[] args) throws IOException {
        final String carbonDioxide = "CO2";

        final SecureRandom random = new SecureRandom();
        final ObjectMapper mapper = new ObjectMapper();

        final String fullyQualifiedNamespace = "my-event-hubs-namespace.servicebus.windows.net";
        final String eventHubName = "event-hub-name";
        final TokenCredential credential = new DefaultAzureCredentialBuilder()
                .build();

        final Reporter reporter = new Reporter(fullyQualifiedNamespace, eventHubName, credential, mapper);

        // Every 3 seconds, a "measurement" is emitted from my IoT device.
        final Disposable subscription = Flux.interval(Duration.ofSeconds(3))
                .flatMap(index -> {
                    int i = Long.valueOf(index % 3).intValue();

                    switch (i) {
                        case 0:
                            double temp = random.nextDouble() * 100.00;
                            return reporter.report(new Temperature(temp, TemperatureUnit.CELSIUS));
                        case 1:
                            double value = random.nextDouble() * 100.00;
                            Humidity measurement = new Humidity(value, 1);
                            return reporter.report(measurement);
                        case 2:
                            int ppm = random.nextInt(1000);
                            return reporter.report(new ParticleConcentration(carbonDioxide, ppm));
                        default:
                            return Mono.error(new IllegalArgumentException("Did not expect that index: " + i));
                    }
                }).subscribe();

        System.out.println("Transmitting telemetry. Press any key to exit.");
        System.in.read();
        System.out.println("Stopping");

        subscription.dispose();
        reporter.close();

        // Blocking so that the program does not end before we are done.
    }
}
