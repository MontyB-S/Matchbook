package com.monty.matchbook.matching;

import com.monty.matchbook.config.KafkaTopics;
import com.monty.matchbook.engine.MatchingEngine;
import com.monty.matchbook.engine.model.MatchResult;
import com.monty.matchbook.engine.model.Order;
import com.monty.matchbook.engine.model.Trade;
import com.monty.matchbook.event.OrderAccepted;
import com.monty.matchbook.event.OrderCancelled;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka to engine and back. A thin adapter — routing and mapping only, no matching logic.
 *
 */
@Component
class OrderCommandListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandListener.class);

    private final MatchingEngine engine;
    private final TradePublisher tradePublisher;

    OrderCommandListener(MatchingEngine engine, TradePublisher tradePublisher) {
        this.engine = engine;
        this.tradePublisher = tradePublisher;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_COMMANDS)
    void onCommand(ConsumerRecord<String, Object> record) {
        log.debug(
                "symbol={} partition={} offset={} thread={}",
                record.key(),
                record.partition(),
                record.offset(),
                Thread.currentThread().getName());

        switch (record.value()) {
            case OrderAccepted accepted -> match(accepted);
            case OrderCancelled cancelled -> engine.cancel(cancelled.symbol(), cancelled.orderId());
            default -> log.warn("ignoring unrecognised command on {}", KafkaTopics.ORDER_COMMANDS);
        }
    }

    private void match(OrderAccepted accepted) {
        MatchResult result = engine.submit(toOrder(accepted));

        for (Trade trade : result.trades()) {
            tradePublisher.publish(trade);
        }
    }

    private static Order toOrder(OrderAccepted accepted) {
        return new Order(
                accepted.orderId(),
                accepted.clientId(),
                accepted.symbol(),
                accepted.side(),
                accepted.type(),
                accepted.priceTicks() == null ? 0 : accepted.priceTicks(),
                accepted.quantity(),
                System.nanoTime());
    }
}
