package com.monty.matchbook.gateway.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SubmitOrderRequestTest {

    private static final BigDecimal PRICE = new BigDecimal("1.52");
    private static final String CLIENT = "client-1";
    private static final String SYMBOL = "AAPL";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Nested
    class ValidRequests {

        @Test
        void limitOrderWithAPriceIsValid() {
            assertThat(validate(limit(PRICE, 100L))).isEmpty();
        }

        @Test
        void marketOrderWithoutAPriceIsValid() {
            assertThat(validate(market(null, 100L))).isEmpty();
        }
    }

    @Nested
    class RequiredFields {

        @Test
        void nullClientIdIsRejected() {
            assertThat(violatedPaths(identifiers(null, SYMBOL))).containsExactly("clientId");
        }

        @Test
        void blankClientIdIsRejected() {
            assertThat(violatedPaths(identifiers("   ", SYMBOL))).containsExactly("clientId");
        }

        @Test
        void nullSymbolIsRejected() {
            assertThat(violatedPaths(identifiers(CLIENT, null))).containsExactly("symbol");
        }

        @Test
        void blankSymbolIsRejected() {
            assertThat(violatedPaths(identifiers(CLIENT, ""))).containsExactly("symbol");
        }

        @Test
        void nullSideIsRejected() {
            SubmitOrderRequest request = new SubmitOrderRequest(CLIENT, SYMBOL, null, OrderType.LIMIT, PRICE, 100L);

            assertThat(violatedPaths(request)).containsExactly("side");
        }

        @Test
        void nullTypeProducesOnlyOneViolation() {
            SubmitOrderRequest request = new SubmitOrderRequest(CLIENT, SYMBOL, Side.BUY, null, PRICE, 100L);

            assertThat(violatedPaths(request)).containsExactly("type");
        }

        @Test
        void nullQuantityIsRejected() {
            assertThat(violatedPaths(limit(PRICE, null))).containsExactly("quantity");
        }
    }

    @Nested
    class Quantity {

        @Test
        void zeroIsRejected() {
            assertThat(violatedPaths(limit(PRICE, 0L))).containsExactly("quantity");
        }

        @Test
        void negativeIsRejected() {
            assertThat(violatedPaths(limit(PRICE, -1L))).containsExactly("quantity");
        }
    }

    @Nested
    class PriceAndType {

        @Test
        void limitOrderWithoutAPriceIsRejected() {
            assertThat(validate(limit(null, 100L))).hasSize(1);
        }

        @Test
        void marketOrderWithAPriceIsRejected() {
            assertThat(validate(market(PRICE, 100L))).hasSize(1);
        }

        @Test
        void zeroPriceIsRejected() {
            assertThat(validate(limit(BigDecimal.ZERO, 100L))).isNotEmpty();
        }

        @Test
        void negativePriceIsRejected() {
            assertThat(validate(limit(new BigDecimal("-1.52"), 100L))).isNotEmpty();
        }

        @Test
        void fractionalPricesAreAccepted() {
            assertThat(validate(limit(new BigDecimal("0.01"), 100L))).isEmpty();
        }
    }

    private static SubmitOrderRequest limit(BigDecimal price, Long quantity) {
        return new SubmitOrderRequest(CLIENT, SYMBOL, Side.BUY, OrderType.LIMIT, price, quantity);
    }

    private static SubmitOrderRequest market(BigDecimal price, Long quantity) {
        return new SubmitOrderRequest(CLIENT, SYMBOL, Side.BUY, OrderType.MARKET, price, quantity);
    }

    private static SubmitOrderRequest identifiers(String clientId, String symbol) {
        return new SubmitOrderRequest(clientId, symbol, Side.BUY, OrderType.LIMIT, PRICE, 100L);
    }

    private static Set<ConstraintViolation<SubmitOrderRequest>> validate(SubmitOrderRequest request) {
        return validator.validate(request);
    }

    private static Set<String> violatedPaths(SubmitOrderRequest request) {
        return validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
