package com.monty.matchbook.engine.book;

import static org.assertj.core.api.Assertions.*;

import com.monty.matchbook.engine.model.Order;
import com.monty.matchbook.support.Orders;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderBookTest {

    private final OrderBook book = new OrderBook(Orders.SYMBOL);

    @Nested
    class BestPrices {

        @Test
        void bestBidIsTheHighestPrice() {
            book.add(Orders.limitBuy(150, 10));
            book.add(Orders.limitBuy(152, 10));
            book.add(Orders.limitBuy(151, 10));

            assertThat(book.bestBid()).hasValue(152);
        }

        @Test
        void bestAskIsTheLowestPrice() {
            book.add(Orders.limitSell(153, 10));
            book.add(Orders.limitSell(151, 10));
            book.add(Orders.limitSell(152, 10));

            assertThat(book.bestAsk()).hasValue(151);
        }

        @Test
        void emptyBookHasNoBestBid() {
            assertThat(book.bestBid()).isEmpty();
        }

        @Test
        void emptyBookHasNoBestAsk() {
            assertThat(book.bestAsk()).isEmpty();
        }

        @Test
        void bidsAndAsksAreIndependent() {
            book.add(Orders.limitBuy(150, 10));

            assertThat(book.bestBid()).hasValue(150);
            assertThat(book.bestAsk()).isEmpty();
        }
    }

    @Nested
    class Depth {

        @Test
        void ordersAtTheSamePriceCombineIntoOneLevel() {
            book.add(Orders.limitBuy(152, 30));
            book.add(Orders.limitBuy(152, 70));

            assertThat(book.depth().bids()).containsExactly(new Level(152, 100));
        }

        @Test
        void bidsAreOrderedHighestFirst() {
            book.add(Orders.limitBuy(150, 10));
            book.add(Orders.limitBuy(152, 20));
            book.add(Orders.limitBuy(151, 30));

            assertThat(book.depth().bids()).containsExactly(new Level(152, 20), new Level(151, 30), new Level(150, 10));
        }

        @Test
        void asksAreOrderedLowestFirst() {
            book.add(Orders.limitSell(153, 10));
            book.add(Orders.limitSell(151, 20));
            book.add(Orders.limitSell(152, 30));

            assertThat(book.depth().asks()).containsExactly(new Level(151, 20), new Level(152, 30), new Level(153, 10));
        }

        @Test
        void emptyBookHasEmptyDepth() {
            assertThat(book.depth().bids()).isEmpty();
            assertThat(book.depth().asks()).isEmpty();
        }

        @Test
        void snapshotCannotBeModified() {
            book.add(Orders.limitBuy(152, 10));
            BookDepth depth = book.depth();

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> depth.bids().add(new Level(999, 1)));
        }

        @Test
        void snapshotDoesNotChangeWhenTheBookChanges() {
            book.add(Orders.limitBuy(152, 10));
            BookDepth snapshot = book.depth();

            book.add(Orders.limitBuy(153, 10));

            assertThat(snapshot.bids()).containsExactly(new Level(152, 10));
        }
    }

    @Nested
    class Cancelling {

        @Test
        void cancellingARestingOrderRemovesItFromDepth() {
            Order order = Orders.limitBuy(152, 10);
            book.add(order);

            assertThat(book.cancel(order.id())).isEqualTo(CancelResult.CANCELLED);
            assertThat(book.depth().bids()).isEmpty();
        }

        @Test
        void cancellingTheOnlyOrderAtAPriceRemovesTheLevel() {
            Order best = Orders.limitBuy(152, 10);
            book.add(best);
            book.add(Orders.limitBuy(151, 10));

            book.cancel(best.id());

            assertThat(book.bestBid()).hasValue(151);
            assertThat(book.depth().bids()).containsExactly(new Level(151, 10));
        }

        @Test
        void cancellingOneOfTwoOrdersAtAPriceKeepsTheLevel() {
            Order first = Orders.limitBuy(152, 30);
            book.add(first);
            book.add(Orders.limitBuy(152, 70));

            book.cancel(first.id());

            assertThat(book.depth().bids()).containsExactly(new Level(152, 70));
        }

        @Test
        void cancellingAnUnknownOrderReturnsNotFound() {
            assertThat(book.cancel(UUID.randomUUID())).isEqualTo(CancelResult.NOT_FOUND);
        }

        @Test
        void cancellingTwiceIsNotAnError() {
            Order order = Orders.limitBuy(152, 10);
            book.add(order);

            assertThat(book.cancel(order.id())).isEqualTo(CancelResult.CANCELLED);
            assertThat(book.cancel(order.id())).isEqualTo(CancelResult.NOT_FOUND);
        }

        @Test
        void cancellingAnAsk() {
            Order order = Orders.limitSell(152, 10);
            book.add(order);

            assertThat(book.cancel(order.id())).isEqualTo(CancelResult.CANCELLED);
            assertThat(book.bestAsk()).isEmpty();
        }
    }

    @Nested
    class Rejection {

        @Test
        void orderForAnotherSymbolIsRejected() {
            Order wrongSymbol = Orders.builder().symbol("TSLA").build();

            assertThatIllegalArgumentException().isThrownBy(() -> book.add(wrongSymbol));
        }

        @Test
        void marketOrderIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> book.add(Orders.marketBuy(10)));
        }

        @Test
        void rejectedOrderDoesNotReachTheBook() {
            Order wrongSymbol = Orders.builder().symbol("TSLA").build();

            assertThatIllegalArgumentException().isThrownBy(() -> book.add(wrongSymbol));

            assertThat(book.depth().bids()).isEmpty();
            assertThat(book.cancel(wrongSymbol.id())).isEqualTo(CancelResult.NOT_FOUND);
        }
    }
}
