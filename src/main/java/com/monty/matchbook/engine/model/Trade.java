package com.monty.matchbook.engine.model;

import java.util.Objects;
import java.util.UUID;

public record Trade(
        UUID id,
        String symbol,
        long sequenceNumber,
        UUID buyOrderId,
        UUID sellOrderId,
        String buyClientId,
        String sellClientId,
        long priceTicks,
        long quantity,
        long executeAtNanos) {

    public Trade {
        Objects.requireNonNull(id);
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(buyOrderId);
        Objects.requireNonNull(sellOrderId);
        Objects.requireNonNull(buyClientId);
        Objects.requireNonNull(sellClientId);

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, it was " + quantity);
        }

        if (priceTicks <= 0) {
            throw new IllegalArgumentException("priceTicks must be positive, it was " + priceTicks);
        }
    }
}
