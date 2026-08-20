package com.monty.matchbook;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Adds a broker to the Postgres container from {@link AbstractIntegrationTest}.
 *
 */
public abstract class AbstractKafkaIntegrationTest extends AbstractIntegrationTest {

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.0.0");

    static {
        KAFKA.start();
    }
}
