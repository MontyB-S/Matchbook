package com.monty.matchbook.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    /**
     * Submits and cancels share one topic. Kafka only orders messages within a partition of a
     * single topic, so on separate topics a cancel could be consumed before the order it cancels.
     */
    public static final String ORDER_COMMANDS = "orders.commands";

    public static final String TRADES_EXECUTED = "trades.executed";

    /**
     * Messages are keyed by symbol, so one symbol always lands on one partition and is consumed by
     * one thread. That is what makes each OrderBook single-threaded without any locking, and it
     * caps matching parallelism at this number. Partition counts cannot be raised later without
     * changing which partition an existing key hashes to.
     */
    private static final int PARTITIONS = 12;

    private static final short REPLICAS = 1;

    @Bean
    NewTopic orderCommands() {
        return TopicBuilder.name(ORDER_COMMANDS)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    NewTopic tradesExecuted() {
        return TopicBuilder.name(TRADES_EXECUTED)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }
}
