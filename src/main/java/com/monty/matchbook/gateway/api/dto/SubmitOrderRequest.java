package com.monty.matchbook.gateway.api.dto;

import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SubmitOrderRequest(
        @NotBlank String clientId,
        @NotBlank String symbol,
        @NotNull Side side,
        @NotNull OrderType type,
        @Positive BigDecimal price,
        @NotNull @Positive Long quantity) {

    @AssertTrue(message = "limit orders require a price; market orders do not need one") public boolean isPriceMatchingWithType() {
        if (type == OrderType.LIMIT && price == null) {
            return false;
        }
        if (type == OrderType.MARKET && price != null) {
            return false;
        }
        return true;
    }
}
