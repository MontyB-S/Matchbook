package com.monty.matchbook.support;

import com.monty.matchbook.engine.model.Order;
import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class Orders {

    public static final String SYMBOL = "AAPL";
    public static final String CLIENT = "client-1";

    private static final AtomicLong CLOCK = new AtomicLong();

    private Orders() {}

    public static Order limitBuy(long priceTicks, long quantity) {
        return builder().side(Side.BUY).limit(priceTicks).quantity(quantity).build();
    }

    public static Order limitSell(long priceTicks, long quantity) {
        return builder().side(Side.SELL).limit(priceTicks).quantity(quantity).build();
    }

    public static Order marketBuy(long quantity) {
        return builder().side(Side.BUY).market().quantity(quantity).build();
    }

    public static Order marketSell(long quantity) {
        return builder().side(Side.SELL).market().quantity(quantity).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id = UUID.randomUUID();
        private String clientId = CLIENT;
        private String symbol = SYMBOL;
        private Side side = Side.BUY;
        private OrderType type = OrderType.LIMIT;
        private long priceTicks = 100;
        private long quantity = 1;
        private long timestampNanos = CLOCK.incrementAndGet();

        private Builder() {}

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder side(Side side) {
            this.side = side;
            return this;
        }

        public Builder quantity(long quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder timestampNanos(long timestampNanos) {
            this.timestampNanos = timestampNanos;
            return this;
        }

        public Builder limit(long priceTicks) {
            this.type = OrderType.LIMIT;
            this.priceTicks = priceTicks;
            return this;
        }

        public Builder market() {
            this.type = OrderType.MARKET;
            this.priceTicks = 0;
            return this;
        }

        public Order build() {
            return new Order(id, clientId, symbol, side, type, priceTicks, quantity, timestampNanos);
        }
    }
}
