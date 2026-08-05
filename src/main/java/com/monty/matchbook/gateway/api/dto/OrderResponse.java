package com.monty.matchbook.gateway.api.dto;

import com.monty.matchbook.engine.model.OrderStatus;
import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String clientId,
        String symbol,
        Side side,
        OrderType type,
        BigDecimal price,
        long quantity,
        long filledQuantity,
        OrderStatus status) {}
