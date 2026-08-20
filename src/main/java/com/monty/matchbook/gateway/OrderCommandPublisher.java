package com.monty.matchbook.gateway;

import com.monty.matchbook.config.KafkaTopics;
import com.monty.matchbook.event.OrderCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes order commands for the matching component.
 *
 */
@Component
public class OrderCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandPublisher.class);

    private final KafkaTemplate<String, Object> kafka;

    OrderCommandPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void publish(OrderCommand command) {
        kafka.send(KafkaTopics.ORDER_COMMANDS, command.symbol(), command).whenComplete((result, failure) -> {
            if (failure != null) {
                log.error(
                        "failed to publish {} for symbol {} — order is in the database but will never reach the engine",
                        command.getClass().getSimpleName(),
                        command.symbol(),
                        failure);
                return;
            }

            log.debug(
                    "published {} for symbol {} to partition {}",
                    command.getClass().getSimpleName(),
                    command.symbol(),
                    result.getRecordMetadata().partition());
        });
    }
}
