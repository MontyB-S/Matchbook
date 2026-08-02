package com.monty.matchbook.engine.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MatchResult(
        UUID orderId, List<Trade> trades, long filledQuantity, long remainingQuantity, OrderStatus status) {

    public MatchResult {
        Objects.requireNonNull(orderId);
        trades = List.copyOf(trades);
    }
}
