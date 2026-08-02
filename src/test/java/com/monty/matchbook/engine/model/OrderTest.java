package com.monty.matchbook.engine.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String CLIENT = "client-1";
    private static final String SYMBOL = "AAPL";

    @Nested
    class ValidOrders {

        @Test
        void limitOrderWithAPriceIsAccepted() {
            Order order = limit(152, 100);

            assertThat(order.priceTicks()).isEqualTo(152);
            assertThat(order.quantity()).isEqualTo(100);
            assertThat(order.type()).isEqualTo(OrderType.LIMIT);
        }

        @Test
        void marketOrderWithoutAPriceIsAccepted() {
            Order order = market(0, 100);

            assertThat(order.priceTicks()).isZero();
            assertThat(order.type()).isEqualTo(OrderType.MARKET);
        }
    }

    @Nested
    class Quantity {

        @Test
        void zeroIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> limit(152, 0));
        }

        @Test
        void negativeIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> limit(152, -1));
        }
    }

    @Nested
    class Price {

        @Test
        void limitOrderWithoutAPriceIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> limit(0, 100));
        }

        @Test
        void limitOrderWithANegativePriceIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> limit(-152, 100));
        }

        @Test
        void marketOrderWithAPriceIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> market(152, 100));
        }
    }

    @Nested
    class Identifiers {

        @Test
        void emptySymbolIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> orderWith(CLIENT, ""));
        }

        @Test
        void whitespaceOnlySymbolIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> orderWith(CLIENT, "   "));
        }

        @Test
        void emptyClientIdIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> orderWith("", SYMBOL));
        }

        @Test
        void whitespaceOnlyClientIdIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> orderWith("   ", SYMBOL));
        }
    }

    @Nested
    class NullReferences {

        @Test
        void nullIdIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Order(null, CLIENT, SYMBOL, Side.BUY, OrderType.LIMIT, 152, 100, 1L));
        }

        @Test
        void nullClientIdIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Order(ID, null, SYMBOL, Side.BUY, OrderType.LIMIT, 152, 100, 1L));
        }

        @Test
        void nullSymbolIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Order(ID, CLIENT, null, Side.BUY, OrderType.LIMIT, 152, 100, 1L));
        }

        @Test
        void nullSideIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Order(ID, CLIENT, SYMBOL, null, OrderType.LIMIT, 152, 100, 1L));
        }

        @Test
        void nullTypeIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new Order(ID, CLIENT, SYMBOL, Side.BUY, null, 152, 100, 1L));
        }
    }

    private static Order limit(long priceTicks, long quantity) {
        return new Order(ID, CLIENT, SYMBOL, Side.BUY, OrderType.LIMIT, priceTicks, quantity, 1L);
    }

    private static Order market(long priceTicks, long quantity) {
        return new Order(ID, CLIENT, SYMBOL, Side.BUY, OrderType.MARKET, priceTicks, quantity, 1L);
    }

    private static Order orderWith(String clientId, String symbol) {
        return new Order(ID, clientId, symbol, Side.BUY, OrderType.LIMIT, 152, 100, 1L);
    }
}
