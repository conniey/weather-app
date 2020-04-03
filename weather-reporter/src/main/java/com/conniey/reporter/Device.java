package com.conniey.reporter;

import com.conniey.models.Humidity;
import com.conniey.models.ParticleConcentration;
import com.conniey.models.Temperature;
import com.conniey.models.TemperatureUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a device that takes measurements from the environment.
 */
public class Device implements AutoCloseable {
    private static final String CARBON_DIOXIDE = "CO2";

    private final Logger logger = LoggerFactory.getLogger(Device.class);
    private final SecureRandom random = new SecureRandom();
    private final AtomicBoolean hasStarted = new AtomicBoolean();
    private final Reporter reporter;

    private Disposable subscription;

    /**
     * Creates an instance that transmits data using the reporter.
     *
     * @param reporter Reporter that transmits data.
     */
    public Device(Reporter reporter) {
        this.reporter = Objects.requireNonNull(reporter, "'reporter' cannot be null");
    }

    /**
     * Starts taking measurements from the environment.
     */
    public void start() {
        if (hasStarted.getAndSet(true)) {
            logger.info("Already started device.");
            return;
        }

        // Every 3 seconds, a "measurement" is emitted from my IoT device.
        subscription = Flux.interval(Duration.ofSeconds(3))
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
                            return reporter.report(new ParticleConcentration(CARBON_DIOXIDE, ppm));
                        default:
                            return Mono.error(new IllegalArgumentException("Did not expect that index: " + i));
                    }
                }).subscribe();
    }

    @Override
    public void close() {
        if (!hasStarted.getAndSet(false)) {
            logger.info("Already stopped.");
            return;
        }

        if (subscription != null) {
            subscription.dispose();
        }
    }
}
