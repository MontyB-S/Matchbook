package com.monty.matchbook.engine.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TradeTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID BUY_ORDER_ID = UUID.randomUUID();
    private static final UUID SELL_ORDER_ID = UUID.randomUUID();
    private static final String SYMBOL = "AAPL";
    private static final String BUYER = "buyer-1";
    private static final String SELLER = "seller-1";

    @Nested
    class ValidTrades {

        @Test
        void isAccepted() {
            Trade trade = trade(152, 100);

            assertThat(trade.priceTicks()).isEqualTo(152);
            assertThat(trade.quantity()).isEqualTo(100);
            assertThat(trade.buyClientId()).isEqualTo(BUYER);
            assertThat(trade.sellClientId()).isEqualTo(SELLER);
        }
    }

    @Nested
    class Quantity {

        @Test
        void zeroIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> trade(152, 0));
        }

        @Test
        void negativeIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> trade(152, -1));
        }
    }

    @Nested
    class Price {

        @Test
        void zeroIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> trade(0, 100));
        }

        @Test
        void negativeIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> trade(-152, 100));
        }
    }

    @Nested
    class NullReferences {

        @Test
        void nullIdIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(() ->
                            new Trade(null, SYMBOL, 1L, BUY_ORDER_ID, SELL_ORDER_ID, BUYER, SELLER, 152, 100, 1L));
        }

        @Test
        void nullSymbolIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(
                            () -> new Trade(ID, null, 1L, BUY_ORDER_ID, SELL_ORDER_ID, BUYER, SELLER, 152, 100, 1L));
        }

        @Test
        void nullBuyOrderIdIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Trade(ID, SYMBOL, 1L, null, SELL_ORDER_ID, BUYER, SELLER, 152, 100, 1L));
        }

        @Test
        void nullSellOrderIdIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Trade(ID, SYMBOL, 1L, BUY_ORDER_ID, null, BUYER, SELLER, 152, 100, 1L));
        }

        @Test
        void nullBuyClientIdIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(
                            () -> new Trade(ID, SYMBOL, 1L, BUY_ORDER_ID, SELL_ORDER_ID, null, SELLER, 152, 100, 1L));
        }

        @Test
        void nullSellClientIdIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(
                            () -> new Trade(ID, SYMBOL, 1L, BUY_ORDER_ID, SELL_ORDER_ID, BUYER, null, 152, 100, 1L));
        }
    }

    private static Trade trade(long priceTicks, long quantity) {
        return new Trade(ID, SYMBOL, 1L, BUY_ORDER_ID, SELL_ORDER_ID, BUYER, SELLER, priceTicks, quantity, 1L);
    }
}
