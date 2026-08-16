package com.monty.matchbook.event;

import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import java.time.Instant;
import java.util.UUID;

public record OrderAccepted(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        String clientId,
        String symbol,
        Side side,
        OrderType type,
        Long priceTicks,
        long quantity)
        implements OrderCommand {}
