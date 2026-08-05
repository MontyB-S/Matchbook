package com.monty.matchbook.gateway.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceConverter {

    private final BigDecimal tickSize;
    private final int displayScale;

    public PriceConverter(BigDecimal tickSize) {
        if (tickSize == null) {
            throw new IllegalArgumentException("tickSize must not be null");
        }
        if (tickSize.signum() <= 0) {
            throw new IllegalArgumentException("tickSize must be positive, it was " + tickSize);
        }

        this.tickSize = tickSize;
        this.displayScale = Math.max(0, tickSize.stripTrailingZeros().scale());
    }

    /**
     * @throws InvalidPriceException if the price is not a whole number of ticks, or is too large to
     * hold in a long
     */
    public long toTicks(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("price must not be null");
        }

        try {
            return price.divide(tickSize, 0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException cause) {
            throw new InvalidPriceException(
                    "price %s is not a whole number of ticks of %s".formatted(price.toPlainString(), tickSize), cause);
        }
    }

    public BigDecimal toDecimal(long ticks) {
        return BigDecimal.valueOf(ticks).multiply(tickSize).setScale(displayScale, RoundingMode.UNNECESSARY);
    }

    public BigDecimal tickSize() {
        return tickSize;
    }
}
