package com.monty.matchbook.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.monty.matchbook.engine.book.CancelResult;
import com.monty.matchbook.engine.book.Level;
import com.monty.matchbook.engine.model.MatchResult;
import com.monty.matchbook.engine.model.Order;
import com.monty.matchbook.engine.model.Side;
import com.monty.matchbook.support.Orders;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();

    @Nested
    class Routing {

        @Test
        void anOrderForAnUnseenSymbolCreatesItsBook() {
            MatchResult result = engine.submit(limitBuy("AAPL", 152, 100));

            assertThat(result.trades()).isEmpty();
            assertThat(engine.depth("AAPL").bids()).containsExactly(new Level(152, 100));
        }

        @Test
        void ordersForTheSameSymbolMatchEachOther() {
            // given
            engine.submit(limitSell("AAPL", 152, 100));

            // when
            MatchResult result = engine.submit(limitBuy("AAPL", 152, 100));

            // then
            assertThat(result.trades()).hasSize(1);
        }

        @Test
        void ordersForDifferentSymbolsDoNotMatch() {
            // given
            engine.submit(limitSell("TSLA", 152, 100));

            // when
            MatchResult result = engine.submit(limitBuy("AAPL", 152, 100));

            // then
            assertThat(result.trades()).isEmpty();
        }

        @Test
        void eachSymbolKeepsItsOwnBook() {
            // given
            engine.submit(limitBuy("AAPL", 152, 100));
            engine.submit(limitBuy("TSLA", 900, 5));

            // then
            assertThat(engine.depth("AAPL").bids()).containsExactly(new Level(152, 100));
            assertThat(engine.depth("TSLA").bids()).containsExactly(new Level(900, 5));
        }
    }

    @Nested
    class Cancelling {

        @Test
        void cancelRoutesToTheBookHoldingTheOrder() {
            // given
            Order order = limitSell("AAPL", 152, 100);
            engine.submit(order);

            // when
            CancelResult result = engine.cancel("AAPL", order.id());

            // then
            assertThat(result).isEqualTo(CancelResult.CANCELLED);
            assertThat(engine.depth("AAPL").asks()).isEmpty();
        }

        @Test
        void cancellingWithTheWrongSymbolDoesNotFindTheOrder() {
            // given
            Order order = limitSell("AAPL", 152, 100);
            engine.submit(order);
            engine.submit(limitSell("TSLA", 900, 5));

            // then
            assertThat(engine.cancel("TSLA", order.id())).isEqualTo(CancelResult.NOT_FOUND);
            assertThat(engine.cancel("AAPL", order.id())).isEqualTo(CancelResult.CANCELLED);
        }

        @Test
        void cancellingAnUnknownSymbolIsNotAnError() {
            assertThat(engine.cancel("NOSUCHSYMBOL", UUID.randomUUID())).isEqualTo(CancelResult.NOT_FOUND);
        }

        @Test
        void cancellingAnUnknownOrderInAKnownSymbolIsNotAnError() {
            // given
            engine.submit(limitSell("AAPL", 152, 100));

            // then
            assertThat(engine.cancel("AAPL", UUID.randomUUID())).isEqualTo(CancelResult.NOT_FOUND);
        }
    }

    @Nested
    class Depth {

        @Test
        void depthForAnUnknownSymbolIsEmpty() {
            assertThat(engine.depth("NOSUCHSYMBOL").bids()).isEmpty();
            assertThat(engine.depth("NOSUCHSYMBOL").asks()).isEmpty();
        }

        @Test
        void depthReflectsBothSides() {
            // given
            engine.submit(limitBuy("AAPL", 151, 100));
            engine.submit(limitSell("AAPL", 153, 200));

            // then
            assertThat(engine.depth("AAPL").bids()).containsExactly(new Level(151, 100));
            assertThat(engine.depth("AAPL").asks()).containsExactly(new Level(153, 200));
        }

        @Test
        void queryingAnUnknownSymbolDoesNotCreateABook() {
            // given
            engine.depth("GHOST");

            // then
            assertThat(engine.bookCount()).isZero();
        }

        @Test
        void submittingCreatesExactlyOneBookPerSymbol() {
            // given
            engine.submit(limitBuy("AAPL", 152, 100));
            engine.submit(limitBuy("AAPL", 151, 100));
            engine.submit(limitBuy("TSLA", 900, 5));

            // then
            assertThat(engine.bookCount()).isEqualTo(2);
        }
    }

    private static Order limitBuy(String symbol, long priceTicks, long quantity) {
        return Orders.builder()
                .symbol(symbol)
                .side(Side.BUY)
                .limit(priceTicks)
                .quantity(quantity)
                .build();
    }

    private static Order limitSell(String symbol, long priceTicks, long quantity) {
        return Orders.builder()
                .symbol(symbol)
                .side(Side.SELL)
                .limit(priceTicks)
                .quantity(quantity)
                .build();
    }
}
