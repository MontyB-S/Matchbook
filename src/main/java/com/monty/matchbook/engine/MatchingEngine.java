package com.monty.matchbook.engine;

import com.monty.matchbook.engine.book.BookDepth;
import com.monty.matchbook.engine.book.CancelResult;
import com.monty.matchbook.engine.book.OrderBook;
import com.monty.matchbook.engine.model.MatchResult;
import com.monty.matchbook.engine.model.Order;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchingEngine {
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();

    public MatchResult submit(Order order) {

        return books.computeIfAbsent(order.symbol(), OrderBook::new).submit(order);
    }

    public CancelResult cancel(String symbol, UUID orderId) {
        OrderBook orderBook = books.get(symbol);
        return orderBook == null ? CancelResult.NOT_FOUND : orderBook.cancel(orderId);
    }

    public BookDepth depth(String symbol) {
        OrderBook orderBook = books.get(symbol);
        return orderBook == null ? new BookDepth(List.of(), List.of()) : orderBook.depth();
    }

    public int bookCount() {
        return books.size();
    }
}
