package com.monty.matchbook.gateway;

import com.monty.matchbook.engine.model.OrderStatus;
import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.event.OrderAccepted;
import com.monty.matchbook.event.OrderCancelled;
import com.monty.matchbook.gateway.api.OrderNotFoundException;
import com.monty.matchbook.gateway.api.PriceConverter;
import com.monty.matchbook.gateway.api.dto.CancelOrderResponse;
import com.monty.matchbook.gateway.api.dto.CancelOutcome;
import com.monty.matchbook.gateway.api.dto.OrderResponse;
import com.monty.matchbook.gateway.api.dto.SubmitOrderRequest;
import com.monty.matchbook.gateway.api.dto.SubmitOrderResponse;
import com.monty.matchbook.gateway.domain.OrderEntity;
import com.monty.matchbook.gateway.domain.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final PriceConverter priceConverter;
    private final OrderRepository orderRepository;
    private final OrderWriter writer;
    private final Clock clock;

    public OrderService(
            PriceConverter priceConverter, OrderRepository orderRepository, OrderWriter writer, Clock clock) {
        this.priceConverter = priceConverter;
        this.orderRepository = orderRepository;
        this.writer = writer;
        this.clock = clock;
    }

    public SubmitOrderResponse submitOrder(String idempotencyKey, SubmitOrderRequest request) {
        Optional<OrderEntity> existing = orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            return toSubmitResponse(existing.get());
        }

        OrderEntity order = newOrder(idempotencyKey, request);

        try {
            return toSubmitResponse(writer.insert(order, toAcceptedEvent(order)));
        } catch (DataIntegrityViolationException duplicateKey) {

            return orderRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .map(OrderService::toSubmitResponse)
                    .orElseThrow(() -> duplicateKey);
        }
    }

    public CancelOrderResponse cancelOrder(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return new CancelOrderResponse(orderId, CancelOutcome.ALREADY_CANCELLED);
        }

        if (order.getStatus().isFinished()) {
            return new CancelOrderResponse(orderId, CancelOutcome.TOO_LATE);
        }

        order.setStatus(OrderStatus.CANCELLED);

        writer.update(order, new OrderCancelled(UUID.randomUUID(), clock.instant(), orderId, order.getSymbol()));

        return new CancelOrderResponse(orderId, CancelOutcome.REQUESTED);
    }

    private OrderAccepted toAcceptedEvent(OrderEntity order) {
        return new OrderAccepted(
                UUID.randomUUID(),
                clock.instant(),
                order.getId(),
                order.getClientId(),
                order.getSymbol(),
                order.getSide(),
                order.getType(),
                order.getPriceTicks(),
                order.getQuantity());
    }

    public OrderResponse get(UUID orderId) {
        return orderRepository
                .findById(orderId)
                .map(this::toOrderResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private OrderEntity newOrder(String idempotencyKey, SubmitOrderRequest request) {
        return new OrderEntity(
                UUID.randomUUID(),
                idempotencyKey,
                request.clientId(),
                request.symbol(),
                request.side(),
                request.type(),
                toPriceTicks(request),
                request.quantity());
    }

    private Long toPriceTicks(SubmitOrderRequest request) {
        return request.type() == OrderType.LIMIT ? priceConverter.toTicks(request.price()) : null;
    }

    private static SubmitOrderResponse toSubmitResponse(OrderEntity order) {
        return new SubmitOrderResponse(order.getId(), order.getStatus());
    }

    private OrderResponse toOrderResponse(OrderEntity order) {
        BigDecimal price = order.getPriceTicks() == null ? null : priceConverter.toDecimal(order.getPriceTicks());

        return new OrderResponse(
                order.getId(),
                order.getClientId(),
                order.getSymbol(),
                order.getSide(),
                order.getType(),
                price,
                order.getQuantity(),
                order.getFilledQuantity(),
                order.getStatus());
    }
}
