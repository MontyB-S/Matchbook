package com.monty.matchbook.matching;

import com.monty.matchbook.config.KafkaTopics;
import com.monty.matchbook.engine.model.Trade;
import com.monty.matchbook.event.TradeExecuted;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes executions for the position component.
 */
@Component
class TradePublisher {

    private static final Logger log = LoggerFactory.getLogger(TradePublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final Clock clock;

    TradePublisher(KafkaTemplate<String, Object> kafka, Clock clock) {
        this.kafka = kafka;
        this.clock = clock;
    }

    void publish(Trade trade) {
        TradeExecuted event = toEvent(trade);

        kafka.send(KafkaTopics.TRADES_EXECUTED, event.symbol(), event).whenComplete((result, failure) -> {
            if (failure != null) {
                log.error("failed to publish trade {} for symbol {}", event.tradeId(), event.symbol(), failure);
            }
        });
    }

    private TradeExecuted toEvent(Trade trade) {
        return new TradeExecuted(
                UUID.randomUUID(),
                clock.instant(),
                trade.id(),
                trade.symbol(),
                trade.sequenceNumber(),
                trade.buyOrderId(),
                trade.sellOrderId(),
                trade.buyClientId(),
                trade.sellClientId(),
                trade.priceTicks(),
                trade.quantity());
    }
}
