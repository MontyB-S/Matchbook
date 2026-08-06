package com.monty.matchbook.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.monty.matchbook.AbstractIntegrationTest;
import com.monty.matchbook.engine.model.OrderStatus;
import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import com.monty.matchbook.gateway.api.dto.SubmitOrderRequest;
import com.monty.matchbook.gateway.api.dto.SubmitOrderResponse;
import com.monty.matchbook.gateway.domain.OrderEntity;
import com.monty.matchbook.gateway.domain.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void clearOrders() {
        orderRepository.deleteAll();
    }

    @Nested
    class Idempotency {

        @Test
        void theSameKeyTwiceCreatesOneOrder() {
            SubmitOrderResponse first = orderService.submitOrder("key-1", limitOrder());
            SubmitOrderResponse second = orderService.submitOrder("key-1", limitOrder());

            assertThat(second.orderId()).isEqualTo(first.orderId());
            assertThat(orderRepository.count()).isEqualTo(1);
        }

        @Test
        void theSameKeyReturnsTheOriginalOrderNotTheNewRequest() {
            SubmitOrderResponse first = orderService.submitOrder("key-1", limitOrder());

            SubmitOrderRequest different = new SubmitOrderRequest(
                    "someone-else", "TSLA", Side.SELL, OrderType.LIMIT, new BigDecimal("99.99"), 5L);
            orderService.submitOrder("key-1", different);

            OrderEntity stored = orderRepository.findById(first.orderId()).orElseThrow();

            assertThat(stored.getClientId()).isEqualTo("client-1");
            assertThat(stored.getSymbol()).isEqualTo("AAPL");
            assertThat(orderRepository.count()).isEqualTo(1);
        }

        @Test
        void differentKeysCreateSeparateOrders() {
            SubmitOrderResponse first = orderService.submitOrder("key-1", limitOrder());
            SubmitOrderResponse second = orderService.submitOrder("key-2", limitOrder());

            assertThat(second.orderId()).isNotEqualTo(first.orderId());
            assertThat(orderRepository.count()).isEqualTo(2);
        }

        @Test
        void concurrentRequestsWithTheSameKeyCreateOneOrder() throws Exception {
            int threads = 8;
            CountDownLatch startLine = new CountDownLatch(1);

            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                List<Future<SubmitOrderResponse>> submissions = IntStream.range(0, threads)
                        .mapToObj(i -> pool.submit(() -> {
                            startLine.await();
                            return orderService.submitOrder("race-key", limitOrder());
                        }))
                        .toList();

                startLine.countDown();

                Set<UUID> orderIds = submissions.stream()
                        .map(OrderServiceIntegrationTest::get)
                        .map(SubmitOrderResponse::orderId)
                        .collect(Collectors.toSet());

                assertThat(orderIds).hasSize(1);
            }

            assertThat(orderRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    class Persistence {

        @Test
        void aLimitOrderStoresItsPriceInTicks() {
            SubmitOrderResponse response = orderService.submitOrder("key-1", limitOrder());

            OrderEntity stored = orderRepository.findById(response.orderId()).orElseThrow();

            assertThat(stored.getPriceTicks()).isEqualTo(152L);
        }

        @Test
        void aMarketOrderStoresNoPrice() {
            SubmitOrderRequest market =
                    new SubmitOrderRequest("client-1", "AAPL", Side.BUY, OrderType.MARKET, null, 100L);

            SubmitOrderResponse response = orderService.submitOrder("key-1", market);
            OrderEntity stored = orderRepository.findById(response.orderId()).orElseThrow();

            assertThat(stored.getPriceTicks()).isNull();
        }

        @Test
        void aNewOrderStartsUnfilledAndNew() {
            SubmitOrderResponse response = orderService.submitOrder("key-1", limitOrder());

            OrderEntity stored = orderRepository.findById(response.orderId()).orElseThrow();

            assertThat(stored.getStatus()).isEqualTo(OrderStatus.NEW);
            assertThat(stored.getFilledQuantity()).isZero();
            assertThat(stored.getVersion()).isZero();
        }

        @Test
        void timestampsArePopulated() {
            SubmitOrderResponse response = orderService.submitOrder("key-1", limitOrder());

            OrderEntity stored = orderRepository.findById(response.orderId()).orElseThrow();

            assertThat(stored.getCreatedAt()).isNotNull();
            assertThat(stored.getUpdatedAt()).isNotNull();
        }

        @Test
        void enumsAreStoredAsStrings() {
            SubmitOrderResponse response = orderService.submitOrder("key-1", limitOrder());

            OrderEntity stored = orderRepository.findById(response.orderId()).orElseThrow();

            assertThat(stored.getSide()).isEqualTo(Side.BUY);
            assertThat(stored.getType()).isEqualTo(OrderType.LIMIT);
        }
    }

    private static SubmitOrderRequest limitOrder() {
        return new SubmitOrderRequest("client-1", "AAPL", Side.BUY, OrderType.LIMIT, new BigDecimal("1.52"), 100L);
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
