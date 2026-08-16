package com.monty.matchbook.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class OrderCommandSerialisationTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T12:00:00Z");

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Nested
    class RoundTrip {

        @Test
        void anAcceptedLimitOrderSurvives() {
            // given
            OrderCommand original = acceptedLimitOrder();

            // when
            OrderCommand restored = mapper.readValue(mapper.writeValueAsString(original), OrderCommand.class);

            // then
            assertThat(restored).isEqualTo(original).isInstanceOf(OrderAccepted.class);
        }

        @Test
        void anAcceptedMarketOrderSurvivesWithoutAPrice() {
            // given
            OrderCommand original = new OrderAccepted(
                    EVENT_ID, OCCURRED_AT, ORDER_ID, "client-1", "AAPL", Side.BUY, OrderType.MARKET, null, 100);

            // when
            OrderCommand restored = mapper.readValue(mapper.writeValueAsString(original), OrderCommand.class);

            // then
            assertThat(restored).isEqualTo(original);
            assertThat(((OrderAccepted) restored).priceTicks()).isNull();
        }

        @Test
        void aCancellationSurvives() {
            // given
            OrderCommand original = new OrderCancelled(EVENT_ID, OCCURRED_AT, ORDER_ID, "AAPL");

            // when
            OrderCommand restored = mapper.readValue(mapper.writeValueAsString(original), OrderCommand.class);

            // then
            assertThat(restored).isEqualTo(original).isInstanceOf(OrderCancelled.class);
        }

        @Test
        void aTradeSurvives() {
            // given
            TradeExecuted original = new TradeExecuted(
                    EVENT_ID,
                    OCCURRED_AT,
                    UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    "AAPL",
                    1L,
                    ORDER_ID,
                    UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    "buyer",
                    "seller",
                    152,
                    100);

            // when
            TradeExecuted restored = mapper.readValue(mapper.writeValueAsString(original), TradeExecuted.class);

            // then
            assertThat(restored).isEqualTo(original);
        }
    }

    @Nested
    class WireFormat {

        @Test
        void theDiscriminatorIsWrittenAsAStableName() {
            String json = mapper.writeValueAsString(acceptedLimitOrder());

            assertThat(json).contains("\"eventType\":\"ORDER_ACCEPTED\"").doesNotContain("com.monty.matchbook");
        }

        @Test
        void aCancellationCarriesItsOwnDiscriminator() {
            String json = mapper.writeValueAsString(new OrderCancelled(EVENT_ID, OCCURRED_AT, ORDER_ID, "AAPL"));

            assertThat(json).contains("\"eventType\":\"ORDER_CANCELLED\"");
        }

        @Test
        void timestampsAreWrittenAsIso8601() {
            String json = mapper.writeValueAsString(acceptedLimitOrder());

            assertThat(json).contains("2026-01-01T12:00:00Z");
        }

        @Test
        void thereIsExactlyOneTimestampField() {
            String json = mapper.writeValueAsString(acceptedLimitOrder());

            assertThat(json.split("ccurredAt", -1)).hasSize(2);
            assertThat(json).doesNotContain("occuredAt");
        }

        @Test
        void anUnknownFieldOnTheWireIsIgnored() {
            String json = """
                    {"eventType":"ORDER_ACCEPTED","eventId":"11111111-1111-1111-1111-111111111111",
                     "occurredAt":"2026-01-01T12:00:00Z","orderId":"22222222-2222-2222-2222-222222222222",
                     "clientId":"client-1","symbol":"AAPL","side":"BUY","type":"LIMIT",
                     "priceTicks":152,"quantity":100,"somethingAddedLater":"value"}
                    """;

            OrderCommand restored = mapper.readValue(json, OrderCommand.class);

            assertThat(restored).isInstanceOf(OrderAccepted.class);
        }
    }

    private static OrderAccepted acceptedLimitOrder() {
        return new OrderAccepted(
                EVENT_ID, OCCURRED_AT, ORDER_ID, "client-1", "AAPL", Side.BUY, OrderType.LIMIT, 152L, 100);
    }
}
