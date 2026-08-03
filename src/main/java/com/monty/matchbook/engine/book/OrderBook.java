package com.monty.matchbook.engine.book;

import com.monty.matchbook.engine.model.*;
import java.util.*;

public final class OrderBook {

    private final String symbol;
    private final TreeMap<Long, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, PriceLevel> asks = new TreeMap<>();
    private final Map<UUID, RestingOrder> byId = new HashMap<>();

    private long sequenceNumber = 0;

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public MatchResult submit(Order incoming) {
        validateSymbol(incoming.symbol());

        List<Trade> trades = new ArrayList<>();
        long remaining = incoming.quantity();
        TreeMap<Long, PriceLevel> opposite = incoming.side() == Side.BUY ? asks : bids;

        while (remaining > 0 && !opposite.isEmpty()) {
            Map.Entry<Long, PriceLevel> entry = opposite.firstEntry();
            long price = entry.getKey();
            PriceLevel level = entry.getValue();

            if (incoming.type() == OrderType.LIMIT && !incoming.side().crosses(incoming.priceTicks(), price)) {
                break;
            }

            while (remaining > 0 && !level.isEmpty()) {
                RestingOrder resting = level.peekFirst();
                long tradeQty = Math.min(remaining, resting.getRemainingQuantity());
                boolean restingExhausted = tradeQty == resting.getRemainingQuantity();

                trades.add(newTrade(incoming, resting.getOrder(), price, tradeQty));

                level.fillFront(tradeQty);
                remaining -= tradeQty;

                if (restingExhausted) {
                    byId.remove(resting.getOrder().id());
                }
            }
            if (level.isEmpty()) {
                opposite.remove(price);
            }
        }

        if (remaining > 0 && incoming.type() == OrderType.LIMIT) {
            RestingOrder resting = new RestingOrder(incoming);
            resting.setRemainingQuantity(remaining);
            rest(resting);
        }

        long filled = incoming.quantity() - remaining;
        return new MatchResult(incoming.id(), trades, filled, remaining, statusFor(incoming, filled, remaining));
    }

    private Trade newTrade(Order incoming, Order resting, long price, long quantity) {
        boolean incomingIsBuy = incoming.side() == Side.BUY;

        return new Trade(
                UUID.randomUUID(),
                symbol,
                ++sequenceNumber,
                incomingIsBuy ? incoming.id() : resting.id(),
                incomingIsBuy ? resting.id() : incoming.id(),
                incomingIsBuy ? incoming.clientId() : resting.clientId(),
                incomingIsBuy ? resting.clientId() : incoming.clientId(),
                price,
                quantity,
                System.nanoTime());
    }

    void validateSymbol(String symbol) {
        if (!symbol.equals(this.symbol)) {
            throw new IllegalArgumentException("Invalid symbol: " + symbol);
        }
    }

    private static OrderStatus statusFor(Order incoming, long filled, long remaining) {
        if (remaining == 0) {
            return OrderStatus.FILLED;
        }
        if (incoming.type() == OrderType.MARKET) {
            return filled == 0 ? OrderStatus.REJECTED : OrderStatus.CANCELLED;
        }
        return filled == 0 ? OrderStatus.NEW : OrderStatus.PARTIALLY_FILLED;
    }

    void add(Order order) {
        if (!validateAddOrder(order)) {
            throw new IllegalArgumentException("Invalid order");
        }

        rest(new RestingOrder(order));
    }

    private void rest(RestingOrder restingOrder) {
        Order order = restingOrder.getOrder();
        TreeMap<Long, PriceLevel> book = order.side() == Side.BUY ? bids : asks;

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
