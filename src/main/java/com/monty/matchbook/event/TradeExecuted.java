package com.monty.matchbook.event;

import java.time.Instant;
import java.util.UUID;

public record TradeExecuted(
        UUID eventId,
        Instant occurredAt,
        UUID tradeId,
        String symbol,
        long sequenceNumber,
        UUID buyOrderId,
        UUID sellOrderId,
        String buyClientId,
        String sellClientId,
        long priceTicks,
        long quantity) {}
