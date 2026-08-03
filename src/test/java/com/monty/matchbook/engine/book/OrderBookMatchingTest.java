package com.monty.matchbook.engine.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import com.monty.matchbook.engine.model.MatchResult;
import com.monty.matchbook.engine.model.Order;
import com.monty.matchbook.engine.model.OrderStatus;
import com.monty.matchbook.engine.model.Side;
import com.monty.matchbook.engine.model.Trade;
import com.monty.matchbook.support.Orders;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderBookMatchingTest {

    private final OrderBook book = new OrderBook(Orders.SYMBOL);

    @Nested
    class PriceImprovement {

        @Test
        void buyTradesAtTheRestingAskPrice() {
            book.add(Orders.limitSell(152, 300));
            book.add(Orders.limitSell(152, 100));
            book.add(Orders.limitSell(153, 500));

            MatchResult result = book.submit(Orders.limitBuy(153, 600));

            assertThat(result.trades())
                    .extracting(Trade::priceTicks, Trade::quantity)
                    .containsExactly(tuple(152L, 300L), tuple(152L, 100L), tuple(153L, 200L));
        }

        @Test
        void sellTradesAtTheRestingBidPrice() {
            book.add(Orders.limitBuy(153, 300));
            book.add(Orders.limitBuy(153, 100));
            book.add(Orders.limitBuy(152, 500));

            MatchResult result = book.submit(Orders.limitSell(152, 600));

            assertThat(result.trades())
                    .extracting(Trade::priceTicks, Trade::quantity)
                    .containsExactly(tuple(153L, 300L), tuple(153L, 100L), tuple(152L, 200L));
        }

        @Test
        void ordersAtTheSamePriceFillInArrivalOrder() {
            Order first = Orders.limitSell(152, 100);
            Order second = Orders.limitSell(152, 100);
            book.add(first);
            book.add(second);

            MatchResult result = book.submit(Orders.limitBuy(152, 150));

            assertThat(result.trades())
                    .extracting(Trade::sellOrderId, Trade::quantity)
                    .containsExactly(tuple(first.id(), 100L), tuple(second.id(), 50L));
        }
    }

    @Nested
    class Crossing {

        @Test
        void anOrderThatDoesNotCrossDoesNotTrade() {
            book.add(Orders.limitSell(152, 100));

            MatchResult result = book.submit(Orders.limitBuy(150, 100));

            assertThat(result.trades()).isEmpty();
            assertThat(result.status()).isEqualTo(OrderStatus.NEW);
            assertThat(book.bestBid()).hasValue(150);
            assertThat(book.bestAsk()).hasValue(152);
        }

        @Test
        void anExactPriceMatchTrades() {
            book.add(Orders.limitSell(152, 100));

            MatchResult result = book.submit(Orders.limitBuy(152, 100));

            assertThat(result.trades()).hasSize(1);
            assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        void matchingStopsAtTheFirstLevelThatDoesNotCross() {
            book.add(Orders.limitSell(152, 100));
            book.add(Orders.limitSell(154, 100));

            MatchResult result = book.submit(Orders.limitBuy(153, 250));

            assertThat(result.trades()).extracting(Trade::priceTicks).containsExactly(152L);
            assertThat(result.remainingQuantity()).isEqualTo(150);
        }

        @Test
        void theBookIsNeverCrossedAfterSubmit() {
            book.add(Orders.limitSell(152, 100));
            book.add(Orders.limitSell(154, 100));

            book.submit(Orders.limitBuy(153, 250));

            assertThat(book.bestBid()).hasValue(153);
            assertThat(book.bestAsk()).hasValue(154);
            assertThat(book.bestBid().getAsLong()).isLessThan(book.bestAsk().getAsLong());
        }
    }

    @Nested
    class Remainders {

        @Test
        void limitRemainderRestsAtItsOwnPrice() {
            book.add(Orders.limitSell(152, 100));

            book.submit(Orders.limitBuy(153, 250));

            assertThat(book.depth().bids()).containsExactly(new Level(153, 150));
        }

        @Test
        void partiallyFilledLimitOrderIsPartiallyFilled() {
            book.add(Orders.limitSell(152, 100));

            MatchResult result = book.submit(Orders.limitBuy(152, 250));

            assertThat(result.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(result.filledQuantity()).isEqualTo(100);
            assertThat(result.remainingQuantity()).isEqualTo(150);
        }

        @Test
        void aRestingOrderThatIsPartiallyFilledStaysOnTheBook() {
            book.add(Orders.limitSell(152, 300));

            book.submit(Orders.limitBuy(152, 100));

            assertThat(book.depth().asks()).containsExactly(new Level(152, 200));
        }

        @Test
        void aFullyFilledRestingOrderLeavesTheBook() {
            book.add(Orders.limitSell(152, 100));

            book.submit(Orders.limitBuy(152, 100));

            assertThat(book.depth().asks()).isEmpty();
            assertThat(book.bestAsk()).isEmpty();
        }
    }

    @Nested
    class MarketOrders {

        @Test
        void marketBuySweepsLevelsRegardlessOfPrice() {
            book.add(Orders.limitSell(152, 100));
            book.add(Orders.limitSell(153, 100));

            MatchResult result = book.submit(Orders.marketBuy(150));

            assertThat(result.trades())
                    .extracting(Trade::priceTicks, Trade::quantity)
                    .containsExactly(tuple(152L, 100L), tuple(153L, 50L));
            assertThat(result.status()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        void marketOrderOnAnEmptyBookIsRejected() {
            MatchResult result = book.submit(Orders.marketBuy(100));

            assertThat(result.trades()).isEmpty();
            assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
            assertThat(result.filledQuantity()).isZero();
        }

        @Test
        void partiallyFilledMarketOrderIsCancelled() {
            book.add(Orders.limitSell(152, 40));

            MatchResult result = book.submit(Orders.marketBuy(100));

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(result.filledQuantity()).isEqualTo(40);
            assertThat(result.remainingQuantity()).isEqualTo(60);
        }

        @Test
        void marketRemainderNeverRests() {
            book.add(Orders.limitSell(152, 40));

            book.submit(Orders.marketBuy(100));

            assertThat(book.depth().bids()).isEmpty();
            assertThat(book.depth().asks()).isEmpty();
        }

        @Test
        void marketSellSweepsTheBids() {
            book.add(Orders.limitBuy(152, 100));
            book.add(Orders.limitBuy(151, 100));

            MatchResult result = book.submit(Orders.marketSell(150));

            assertThat(result.trades())
                    .extracting(Trade::priceTicks, Trade::quantity)
                    .containsExactly(tuple(152L, 100L), tuple(151L, 50L));
        }
    }

    @Nested
    class CancelIndex {

        @Test
        void aFilledOrderCanNoLongerBeCancelled() {
            Order resting = Orders.limitSell(152, 100);
            book.add(resting);

            book.submit(Orders.limitBuy(152, 100));

            assertThat(book.cancel(resting.id())).isEqualTo(CancelResult.NOT_FOUND);
        }

        @Test
        void aPartiallyFilledOrderCanStillBeCancelled() {
            Order resting = Orders.limitSell(152, 300);
            book.add(resting);

            book.submit(Orders.limitBuy(152, 100));

            assertThat(book.cancel(resting.id())).isEqualTo(CancelResult.CANCELLED);
            assertThat(book.depth().asks()).isEmpty();
        }

        @Test
        void aRestedRemainderCanBeCancelled() {
            book.add(Orders.limitSell(152, 100));
            Order incoming = Orders.limitBuy(153, 250);

            book.submit(incoming);

            assertThat(book.cancel(incoming.id())).isEqualTo(CancelResult.CANCELLED);
            assertThat(book.depth().bids()).isEmpty();
        }
    }

    @Nested
    class Sequencing {

        @Test
        void sequenceNumbersIncrementAcrossTrades() {
            book.add(Orders.limitSell(152, 100));
            book.add(Orders.limitSell(153, 100));

            MatchResult result = book.submit(Orders.limitBuy(153, 200));

            assertThat(result.trades()).extracting(Trade::sequenceNumber).containsExactly(1L, 2L);
        }

        @Test
        void sequenceNumbersContinueAcrossSubmissions() {
            book.add(Orders.limitSell(152, 100));
            book.add(Orders.limitSell(153, 100));

            book.submit(Orders.limitBuy(152, 100));
            MatchResult second = book.submit(Orders.limitBuy(153, 100));

            assertThat(second.trades()).extracting(Trade::sequenceNumber).containsExactly(2L);
        }
    }

    @Nested
    class TradeParties {

        @Test
        void incomingBuyIsTheBuyerAndTheRestingOrderIsTheSeller() {
            Order resting = Orders.builder()
                    .clientId("seller")
                    .side(Side.SELL)
                    .limit(152)
                    .quantity(100)
                    .build();
            book.add(resting);

            Order incoming =
                    Orders.builder().clientId("buyer").limit(152).quantity(100).build();
            MatchResult result = book.submit(incoming);

            Trade trade = result.trades().getFirst();
            assertThat(trade.buyOrderId()).isEqualTo(incoming.id());
            assertThat(trade.sellOrderId()).isEqualTo(resting.id());
            assertThat(trade.buyClientId()).isEqualTo("buyer");
            assertThat(trade.sellClientId()).isEqualTo("seller");
        }

        @Test
        void incomingSellIsTheSellerAndTheRestingOrderIsTheBuyer() {
            Order resting =
                    Orders.builder().clientId("buyer").limit(152).quantity(100).build();
            book.add(resting);

            Order incoming = Orders.builder()
                    .clientId("seller")
                    .side(Side.SELL)
                    .limit(152)
                    .quantity(100)
                    .build();
            MatchResult result = book.submit(incoming);

            Trade trade = result.trades().getFirst();
            assertThat(trade.buyOrderId()).isEqualTo(resting.id());
            assertThat(trade.sellOrderId()).isEqualTo(incoming.id());
            assertThat(trade.buyClientId()).isEqualTo("buyer");
            assertThat(trade.sellClientId()).isEqualTo("seller");
        }
    }

    @Nested
    class Invariants {

        @Test
        void quantityIsConservedWhenFullyFilled() {
            book.add(Orders.limitSell(152, 300));
            Order incoming = Orders.limitBuy(152, 300);

            assertQuantityConserved(incoming, book.submit(incoming));
        }

        @Test
        void quantityIsConservedWhenPartiallyFilled() {
            book.add(Orders.limitSell(152, 100));
            Order incoming = Orders.limitBuy(152, 250);

            assertQuantityConserved(incoming, book.submit(incoming));
        }

        @Test
        void quantityIsConservedWhenNothingCrosses() {
            book.add(Orders.limitSell(152, 100));
            Order incoming = Orders.limitBuy(150, 250);

            assertQuantityConserved(incoming, book.submit(incoming));
        }

        @Test
        void quantityIsConservedAcrossAMultiLevelSweep() {
            book.add(Orders.limitSell(152, 300));
            book.add(Orders.limitSell(152, 100));
            book.add(Orders.limitSell(153, 500));
            Order incoming = Orders.limitBuy(153, 600);

            assertQuantityConserved(incoming, book.submit(incoming));
        }
    }

    @Test
    void submittingAnOrderForAnotherSymbolIsRejected() {
        Order wrongSymbol = Orders.builder().symbol("TSLA").build();

        assertThatIllegalArgumentException().isThrownBy(() -> book.submit(wrongSymbol));
    }

    private static void assertQuantityConserved(Order submitted, MatchResult result) {
        assertThat(result.filledQuantity() + result.remainingQuantity()).isEqualTo(submitted.quantity());

        long traded = result.trades().stream().mapToLong(Trade::quantity).sum();
        assertThat(traded).isEqualTo(result.filledQuantity());
    }
}
