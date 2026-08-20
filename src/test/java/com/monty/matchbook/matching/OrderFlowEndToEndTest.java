package com.monty.matchbook.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monty.matchbook.AbstractKafkaIntegrationTest;
import com.monty.matchbook.config.KafkaTopics;
import com.monty.matchbook.engine.MatchingEngine;
import com.monty.matchbook.event.TradeExecuted;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP to Postgres to Kafka to the engine and back out to Kafka.
 *
 */
@AutoConfigureMockMvc
class OrderFlowEndToEndTest extends AbstractKafkaIntegrationTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MatchingEngine engine;

    private KafkaConsumer<String, String> trades;

    /** A fresh symbol per test — the engine's books outlive a single test method. */
    private String symbol;

    @BeforeEach
    void subscribeToTrades() {
        symbol = "SYM" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        trades = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()));

        trades.subscribe(List.of(KafkaTopics.TRADES_EXECUTED));
    }

    @AfterEach
    void closeConsumer() {
        trades.close();
    }

    @Test
    void anOrderSubmittedOverHttpReachesTheEngineAndProducesATrade() throws Exception {
        // given
        submit("SELL", "1.52", 100);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(engine.depth(symbol).asks()).isNotEmpty());

        // when
        submit("BUY", "1.52", 100);

        // then
        TradeExecuted trade = await().atMost(Duration.ofSeconds(20)).until(this::pollForTrade, t -> t != null);

        assertThat(trade.symbol()).isEqualTo(symbol);
        assertThat(trade.priceTicks()).isEqualTo(152);
        assertThat(trade.quantity()).isEqualTo(100);
        assertThat(trade.buyClientId()).isEqualTo("buyer");
        assertThat(trade.sellClientId()).isEqualTo("seller");
    }

    @Test
    void aRestingOrderCanBeCancelledOverHttp() throws Exception {
        // given
        UUID orderId = submit("SELL", "1.52", 100);
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(engine.depth(symbol).asks()).isNotEmpty());

        // when
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/orders/{id}", orderId))
                .andExpect(status().isOk());

        // then
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(engine.depth(symbol).asks()).isEmpty());
    }

    @Test
    void anOrderThatDoesNotCrossRestsWithoutTrading() throws Exception {
        // given
        submit("SELL", "1.60", 100);

        // when
        submit("BUY", "1.50", 100);

        // then
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(engine.depth(symbol).asks()).isNotEmpty();
            assertThat(engine.depth(symbol).bids()).isNotEmpty();
        });

        assertThat(pollForTrade()).isNull();
    }

    private UUID submit(String side, String price, long quantity) throws Exception {
        String client = "SELL".equals(side) ? "seller" : "buyer";

        String body = """
                {"clientId":"%s","symbol":"%s","side":"%s","type":"LIMIT","price":"%s","quantity":%d}
                """.formatted(client, symbol, side, price, quantity);

        String response = mockMvc.perform(post("/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(JSON.readTree(response).get("orderId").asString());
    }

    private TradeExecuted pollForTrade() {
        ConsumerRecords<String, String> records = trades.poll(Duration.ofMillis(500));
        List<TradeExecuted> matching = new ArrayList<>();

        for (ConsumerRecord<String, String> record : records) {
            TradeExecuted trade = JSON.readValue(record.value(), TradeExecuted.class);
            if (trade.symbol().equals(symbol)) {
                assertThat(record.key()).isEqualTo(symbol);
                matching.add(trade);
            }
        }

        return matching.isEmpty() ? null : matching.getFirst();
    }
}
