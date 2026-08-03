package com.monty.matchbook.engine.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.monty.matchbook.engine.model.MatchResult;
import com.monty.matchbook.engine.model.Order;
import com.monty.matchbook.engine.model.OrderStatus;
import com.monty.matchbook.engine.model.Side;
import com.monty.matchbook.engine.model.Trade;
import com.monty.matchbook.support.Orders;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OrderBookPropertyTest {

    private static final int RUNS = 500;
    private static final int ACTIONS_PER_RUN = 100;

    private static final long MIN_PRICE = 148;
    private static final long MAX_PRICE = 156;

    private static final int MARKET_ORDER_IN = 10;
    private static final int CANCEL_IN = 5;
    private static final int CLIENTS = 4;
    private static final int MAX_QUANTITY = 100;

    @Test
    void invariantsHoldForRandomSequences() {
        long traded = 0;

        for (int run = 0; run < RUNS; run++) {
            long seed = ThreadLocalRandom.current().nextLong();
            try {
                traded += runSequence(seed);
            } catch (AssertionError failure) {
                throw new AssertionError("Failing seed: " + seed, failure);
            }
        }

        assertThat(traded)
                .as("generator produced too little crossing to be a meaningful test")
                .isGreaterThan(400_000L);
    }

    long runSequence(long seed) {
        Random random = new Random(seed);
        OrderBook book = new OrderBook(Orders.SYMBOL);

        Map<UUID, Long> submittedByOrderId = new HashMap<>();
        Map<UUID, Long> filledByOrderId = new HashMap<>();
        List<UUID> restedOrderIds = new ArrayList<>();

        long totalSubmitted = 0;
        long totalTraded = 0;
        long totalCancelled = 0;

        for (int action = 0; action < ACTIONS_PER_RUN; action++) {

            if (!restedOrderIds.isEmpty() && random.nextInt(CANCEL_IN) == 0) {
                UUID target = restedOrderIds.get(random.nextInt(restedOrderIds.size()));

                long before = quantityOnBook(book);
                CancelResult result = book.cancel(target);
                long after = quantityOnBook(book);

                totalCancelled += before - after;

                if (result == CancelResult.CANCELLED) {
                    restedOrderIds.remove(target);
                }
            } else {
                Order order = randomOrder(random);

                submittedByOrderId.put(order.id(), order.quantity());
                totalSubmitted += order.quantity();

                MatchResult result = book.submit(order);

                for (Trade trade : result.trades()) {
                    totalTraded += trade.quantity();
                    filledByOrderId.merge(trade.buyOrderId(), trade.quantity(), Long::sum);
                    filledByOrderId.merge(trade.sellOrderId(), trade.quantity(), Long::sum);
                }

                if (isTerminal(result.status())) {
                    totalCancelled += result.remainingQuantity();
                } else if (result.remainingQuantity() > 0) {
                    restedOrderIds.add(order.id());
                }
            }

            assertBookIsNotCrossed(book, action);
            assertQuantityIsConserved(book, action, totalSubmitted, totalTraded, totalCancelled);
            assertNoOrderIsOverFilled(action, submittedByOrderId, filledByOrderId);
        }

        return totalTraded;
    }

    private static boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED;
    }

    private static void assertBookIsNotCrossed(OrderBook book, int action) {
        if (book.bestBid().isEmpty() || book.bestAsk().isEmpty()) {
            return;
        }

        assertThat(book.bestBid().getAsLong())
                .as("book crossed after action %d", action)
                .isLessThan(book.bestAsk().getAsLong());
    }

    private static void assertQuantityIsConserved(
            OrderBook book, int action, long totalSubmitted, long totalTraded, long totalCancelled) {

        long onBook = quantityOnBook(book);
        long accountedFor = (2 * totalTraded) + onBook + totalCancelled;

        assertThat(accountedFor)
                .as(
                        "quantity not conserved after action %d: traded=%d onBook=%d cancelled=%d",
                        action, totalTraded, onBook, totalCancelled)
                .isEqualTo(totalSubmitted);
    }

    private static void assertNoOrderIsOverFilled(
            int action, Map<UUID, Long> submittedByOrderId, Map<UUID, Long> filledByOrderId) {

        filledByOrderId.forEach((orderId, filled) -> assertThat(filled)
                .as("order %s over-filled after action %d", orderId, action)
                .isLessThanOrEqualTo(submittedByOrderId.get(orderId)));
    }

    private static long quantityOnBook(OrderBook book) {
        BookDepth depth = book.depth();

        return Stream.concat(depth.bids().stream(), depth.asks().stream())
                .mapToLong(Level::quantity)
                .sum();
    }

    private static Order randomOrder(Random random) {
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        long quantity = 1 + random.nextInt(MAX_QUANTITY);
        String clientId = "client-" + random.nextInt(CLIENTS);

        if (random.nextInt(MARKET_ORDER_IN) == 0) {
            return Orders.builder()
                    .clientId(clientId)
                    .side(side)
                    .market()
                    .quantity(quantity)
                    .build();
        }

        long price = MIN_PRICE + random.nextInt((int) (MAX_PRICE - MIN_PRICE + 1));

        return Orders.builder()
                .clientId(clientId)
                .side(side)
                .limit(price)
                .quantity(quantity)
                .build();
    }
}
