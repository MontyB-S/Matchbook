package com.monty.matchbook.engine.model;

import io.micrometer.common.util.StringUtils;
import java.util.Objects;
import java.util.UUID;

public record Order(
        UUID id,
        String clientId,
        String symbol,
        Side side,
        OrderType type,
        long priceTicks,
        long quantity,
        long timestampNanos) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, it was " + quantity);
        }

        if (type == OrderType.LIMIT && priceTicks <= 0) {
            throw new IllegalArgumentException("priceTicks must be positive if it is a Limit order");
        }

        if (type == OrderType.MARKET && priceTicks != 0) {
            throw new IllegalArgumentException("priceTicks must be 0 if it is a Market order");
        }

        if (StringUtils.isBlank(symbol)) {
            throw new IllegalArgumentException("Symbol must not be empty");
        }

        if (StringUtils.isBlank(clientId)) {
            throw new IllegalArgumentException("Client ID must not be empty");
        }
    }
}
