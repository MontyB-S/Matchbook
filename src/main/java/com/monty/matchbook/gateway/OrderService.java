package com.monty.matchbook.gateway;

import com.monty.matchbook.engine.book.CancelResult;
import com.monty.matchbook.engine.model.OrderStatus;
import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import com.monty.matchbook.gateway.api.OrderNotFoundException;
import com.monty.matchbook.gateway.api.PriceConverter;
import com.monty.matchbook.gateway.api.dto.CancelOrderResponse;
import com.monty.matchbook.gateway.api.dto.OrderResponse;
import com.monty.matchbook.gateway.api.dto.SubmitOrderRequest;
import com.monty.matchbook.gateway.api.dto.SubmitOrderResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final PriceConverter priceConverter;
    private final Map<UUID, StoredOrder> orders = new ConcurrentHashMap<>();

    public OrderService(PriceConverter priceConverter) {
        this.priceConverter = priceConverter;
    }

    public SubmitOrderResponse submitOrder(SubmitOrderRequest request) {
        UUID orderId = UUID.randomUUID();
        long priceTicks = request.type() == OrderType.LIMIT ? priceConverter.toTicks(request.price()) : 0;

        orders.put(
                orderId,
                new StoredOrder(
                        orderId,
                        request.clientId(),
                        request.symbol(),
                        request.side(),
                        request.type(),
                        priceTicks,
                        request.quantity(),
                        0,
                        OrderStatus.NEW));

        return new SubmitOrderResponse(orderId, OrderStatus.NEW);
    }

    public CancelOrderResponse cancelOrder(UUID orderId) {

        StoredOrder cancelled =
                orders.computeIfPresent(orderId, (id, order) -> order.withStatus(OrderStatus.CANCELLED));

        return new CancelOrderResponse(orderId, cancelled == null ? CancelResult.NOT_FOUND : CancelResult.CANCELLED);
    }

    public OrderResponse get(UUID orderId) {

        StoredOrder order = orders.get(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }

        StoredOrder storedOrder = orders.get(orderId);
        return new OrderResponse(
                storedOrder.id(),
                storedOrder.clientId(),
                storedOrder.symbol(),
                storedOrder.side(),
                storedOrder.type(),
                priceConverter.toDecimal(storedOrder.priceTicks()),
                storedOrder.quantity(),
                storedOrder.filledQuantity(),
                storedOrder.status());
    }

    private record StoredOrder(
            UUID id,
            String clientId,
            String symbol,
            Side side,
            OrderType type,
            long priceTicks,
            long quantity,
            long filledQuantity,
            OrderStatus status) {

        StoredOrder withStatus(OrderStatus newStatus) {
            return new StoredOrder(id, clientId, symbol, side, type, priceTicks, quantity, filledQuantity, newStatus);
        }
    }
}
