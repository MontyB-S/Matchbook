package com.monty.matchbook.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.monty.matchbook.AbstractKafkaIntegrationTest;
import com.monty.matchbook.config.KafkaTopics;
import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import com.monty.matchbook.gateway.api.dto.SubmitOrderRequest;
import com.monty.matchbook.gateway.api.dto.SubmitOrderResponse;
import com.monty.matchbook.gateway.domain.OrderRepository;
import com.monty.matchbook.gateway.domain.OutboxEntity;
import com.monty.matchbook.gateway.domain.OutboxRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "matchbook.outbox.poll-interval-ms=3600000")
class OutboxCrashRecoveryTest extends AbstractKafkaIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxPublisher publisher;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private OrderRepository orders;

    private KafkaConsumer<String, String> commands;
    private String symbol;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        orders.deleteAll();

        symbol = "SYM" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        commands = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()));

        commands.subscribe(List.of(KafkaTopics.ORDER_COMMANDS));
    }

    @AfterEach
    void closeConsumer() {
        commands.close();
    }

    @Nested
    class WhileThePollerIsDown {

        @Test
        void submittingWritesTheOrderAndItsMessageInOneTransaction() {
            // when
            orderService.submitOrder(key(), limitBuy());

            // then
            assertThat(orders.count()).isEqualTo(1);
            assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(1);
        }

        @Test
        void nothingReachesKafka() {
            // given
            orderService.submitOrder(key(), limitBuy());
            orderService.submitOrder(key(), limitBuy());

            // then
            assertThat(drainKeys()).isEmpty();
            assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(2);
        }

        @Test
        void theStoredMessageCarriesItsRoutingAndPayload() {
            // given
            orderService.submitOrder(key(), limitBuy());

            // when
            OutboxEntity row =
                    outbox.findByPublishedAtIsNullOrderByIdAsc(Limit.of(1)).getFirst();

            // then
            assertThat(row.getTopic()).isEqualTo(KafkaTopics.ORDER_COMMANDS);
            assertThat(row.getMsgKey()).isEqualTo(symbol);
            assertThat(row.getPayload()).contains("ORDER_ACCEPTED").contains(symbol);
            assertThat(row.getPublishedAt()).isNull();
        }
    }

    @Nested
    class WhenThePollerRunsAgain {

        @Test
        void everyPendingMessageIsPublished() {
            // given
            orderService.submitOrder(key(), limitBuy());
            orderService.submitOrder(key(), limitBuy());
            orderService.submitOrder(key(), limitBuy());

            // when
            int published = publisher.publishPending();

            // then
            assertThat(published).isEqualTo(3);
            assertThat(drainKeys()).containsExactly(symbol, symbol, symbol);
            assertThat(outbox.countByPublishedAtIsNull()).isZero();
        }

        @Test
        void aSecondRunPublishesNothingFurther() {
            // given
            orderService.submitOrder(key(), limitBuy());
            publisher.publishPending();
            drainKeys();

            // when
            int published = publisher.publishPending();

            // then
            assertThat(published).isZero();
            assertThat(drainKeys()).isEmpty();
        }

        @Test
        void publishedRowsAreStampedNotDeleted() {
            // given
            orderService.submitOrder(key(), limitBuy());

            // when
            publisher.publishPending();

            // then
            assertThat(outbox.count()).isEqualTo(1);
            assertThat(outbox.findAll().getFirst().getPublishedAt()).isNotNull();
        }

        @Test
        void messagesArePublishedInTheOrderTheyWereWritten() {
            // given
            List<UUID> submitted = List.of(
                    orderService.submitOrder(key(), limitBuy()).orderId(),
                    orderService.submitOrder(key(), limitBuy()).orderId(),
                    orderService.submitOrder(key(), limitBuy()).orderId());

            // when
            publisher.publishPending();

            // then
            assertThat(drainPayloads())
                    .extracting(payload -> submitted.stream()
                            .filter(id -> payload.contains(id.toString()))
                            .findFirst()
                            .orElseThrow())
                    .containsExactlyElementsOf(submitted);
        }
    }

    @Nested
    class Cancelling {

        @Test
        void aCancelIsWrittenToTheOutboxToo() {
            // given
            SubmitOrderResponse submitted = orderService.submitOrder(key(), limitBuy());
            publisher.publishPending();
            drainKeys();

            // when
            orderService.cancelOrder(submitted.orderId());

            // then
            assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(1);
            assertThat(drainKeys()).isEmpty();

            publisher.publishPending();

            assertThat(drainPayloads()).singleElement().asString().contains("ORDER_CANCELLED");
        }
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    private SubmitOrderRequest limitBuy() {
        return new SubmitOrderRequest("client-1", symbol, Side.BUY, OrderType.LIMIT, new BigDecimal("1.52"), 100L);
    }

    private List<String> drainKeys() {
        return drain().stream().map(ConsumerRecord::key).toList();
    }

    private List<String> drainPayloads() {
        return drain().stream().map(ConsumerRecord::value).toList();
    }

    private List<ConsumerRecord<String, String>> drain() {
        List<ConsumerRecord<String, String>> collected = new ArrayList<>();

        for (int attempt = 0; attempt < 4; attempt++) {
            ConsumerRecords<String, String> polled = commands.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : polled) {
                if (symbol.equals(record.key())) {
                    collected.add(record);
                }
            }
        }

        return collected;
    }
}
