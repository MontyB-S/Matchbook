package com.monty.matchbook.engine.book;

import com.monty.matchbook.engine.model.Order;
import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import java.util.*;

public final class OrderBook {

    private final String symbol;
    private final TreeMap<Long, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, PriceLevel> asks = new TreeMap<>();
    private final Map<UUID, RestingOrder> byId = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public void add(Order order) {
        if (!validateAddOrder(order)) {
            throw new IllegalArgumentException("Invalid order");
        }

        TreeMap<Long, PriceLevel> book = order.side() == Side.BUY ? bids : asks;

        RestingOrder restingOrder = new RestingOrder(order);

        book.computeIfAbsent(order.priceTicks(), PriceLevel::new).add(restingOrder);

        byId.put(order.id(), restingOrder);
    }

    private boolean validateAddOrder(Order order) {
        if (!order.symbol().equals(symbol)) {
            return false;
        }
        if (order.type() == OrderType.MARKET) {
            return false;
        }
        return true;
    }

    public CancelResult cancel(UUID orderId) {
        RestingOrder order = byId.remove(orderId);
        if (order == null) {
            return CancelResult.NOT_FOUND;
        }

        TreeMap<Long, PriceLevel> book = order.getOrder().side() == Side.BUY ? bids : asks;

        PriceLevel level = book.get(order.getOrder().priceTicks());

        boolean found = level.remove(order);
        if (!found) {
            throw new IllegalStateException("byId and book have diverged");
        }
        if (level.isEmpty()) {
            book.remove(level.priceTicks());
        }

        return CancelResult.CANCELLED;
    }

    public OptionalLong bestBid() {
        if (bids.isEmpty()) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(bids.firstKey());
    }

    public OptionalLong bestAsk() {
        if (asks.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(asks.firstKey());
    }

    public BookDepth depth() {
        List<Level> bidsLevel = new ArrayList<>();
        List<Level> asksLevel = new ArrayList<>();

        for (Map.Entry<Long, PriceLevel> entry : bids.entrySet()) {
            bidsLevel.add(
                    new Level(entry.getValue().priceTicks(), entry.getValue().totalQuantity()));
        }

        for (Map.Entry<Long, PriceLevel> entry : asks.entrySet()) {
            asksLevel.add(
                    new Level(entry.getValue().priceTicks(), entry.getValue().totalQuantity()));
        }

        return new BookDepth(bidsLevel, asksLevel);
    }
}
