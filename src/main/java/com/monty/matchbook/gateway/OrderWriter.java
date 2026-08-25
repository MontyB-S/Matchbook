package com.monty.matchbook.gateway;

import com.monty.matchbook.config.KafkaTopics;
import com.monty.matchbook.event.OrderCommand;
import com.monty.matchbook.gateway.domain.OrderEntity;
import com.monty.matchbook.gateway.domain.OrderRepository;
import com.monty.matchbook.gateway.domain.OutboxEntity;
import com.monty.matchbook.gateway.domain.OutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
class OrderWriter {

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final ObjectMapper json;

    OrderWriter(OrderRepository orders, OutboxRepository outbox, ObjectMapper json) {
        this.orders = orders;
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional
    OrderEntity insert(OrderEntity order, OrderCommand command) {
        OrderEntity saved = orders.saveAndFlush(order);
        outbox.save(toRow(command));
        return saved;
    }

    @Transactional
    void update(OrderEntity order, OrderCommand command) {
        orders.save(order);
        outbox.save(toRow(command));
    }

    private OutboxEntity toRow(OrderCommand command) {
        return new OutboxEntity(
                command.orderId(), KafkaTopics.ORDER_COMMANDS, command.symbol(), json.writeValueAsString(command));
    }
}
