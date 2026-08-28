package com.monty.matchbook.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.monty.matchbook.config.KafkaTopics;
import com.monty.matchbook.engine.MatchingEngine;
import com.monty.matchbook.engine.book.Level;
import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import com.monty.matchbook.engine.model.Trade;
import com.monty.matchbook.event.OrderAccepted;
import com.monty.matchbook.event.OrderCancelled;
import com.monty.matchbook.event.OrderCommand;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderCommandListenerTest {

    private static final String SYMBOL = "AAPL";

    private final MatchingEngine engine = new MatchingEngine();
    private final TradePublisher trades = mock(TradePublisher.class);
    private final ProcessedEvents processed = new ProcessedEvents(1000);
    private final OrderCommandListener listener = new OrderCommandListener(engine, trades, processed);

    @Nested
    class Deduplication {

        @Test
        void aReplayedOrderDoesNotRestTwice() {
            // given
            OrderAccepted accepted = limitSell(100);

            // when
            listener.onCommand(record(accepted));
            listener.onCommand(record(accepted));

            // then
            assertThat(engine.depth(SYMBOL).asks()).containsExactly(new Level(152, 100));
        }

        @Test
        void aReplayedOrderDoesNotTradeTwice() {
            // given
            listener.onCommand(record(limitSell(100)));
            OrderAccepted buy = limitBuy(100);

            // when
            listener.onCommand(record(buy));
            listener.onCommand(record(buy));

            // then
            verify(trades).publish(org.mockito.ArgumentMatchers.any(Trade.class));
        }

        @Test
        void aReplayedCancelIsHarmless() {
            // given
            OrderAccepted accepted = limitSell(100);
            listener.onCommand(record(accepted));

            OrderCancelled cancelled = new OrderCancelled(UUID.randomUUID(), Instant.now(), accepted.orderId(), SYMBOL);

            // when
            listener.onCommand(record(cancelled));
            listener.onCommand(record(cancelled));

            // then
            assertThat(engine.depth(SYMBOL).asks()).isEmpty();
        }

        @Test
        void distinctOrdersWithTheSameContentBothRest() {
            // when
            listener.onCommand(record(limitSell(100)));
            listener.onCommand(record(limitSell(100)));

            // then
            assertThat(engine.depth(SYMBOL).asks()).containsExactly(new Level(152, 200));
        }
    }

    @Nested
    class Routing {

        @Test
        void anAcceptedOrderReachesTheBook() {
            listener.onCommand(record(limitSell(100)));

            assertThat(engine.depth(SYMBOL).asks()).containsExactly(new Level(152, 100));
        }

        @Test
        void aCancelRemovesTheOrder() {
            // given
            OrderAccepted accepted = limitSell(100);
            listener.onCommand(record(accepted));

            // when
            listener.onCommand(
                    record(new OrderCancelled(UUID.randomUUID(), Instant.now(), accepted.orderId(), SYMBOL)));

            // then
            assertThat(engine.depth(SYMBOL).asks()).isEmpty();
        }

        @Test
        void anUnrecognisedPayloadIsIgnored() {
            listener.onCommand(new ConsumerRecord<>(KafkaTopics.ORDER_COMMANDS, 0, 0L, SYMBOL, "not a command"));

            assertThat(engine.bookCount()).isZero();
            verifyNoInteractions(trades);
        }
    }

    private static OrderAccepted limitSell(long quantity) {
        return accepted(Side.SELL, quantity);
    }

    private static OrderAccepted limitBuy(long quantity) {
        return accepted(Side.BUY, quantity);
    }

    private static OrderAccepted accepted(Side side, long quantity) {
        return new OrderAccepted(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                "client-1",
                SYMBOL,
                side,
                OrderType.LIMIT,
                152L,
                quantity);
    }

    private static ConsumerRecord<String, Object> record(OrderCommand command) {
        return new ConsumerRecord<>(KafkaTopics.ORDER_COMMANDS, 0, 0L, command.symbol(), command);
    }
}
