package com.monty.matchbook.gateway.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monty.matchbook.engine.book.CancelResult;
import com.monty.matchbook.engine.model.OrderStatus;
import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import com.monty.matchbook.gateway.OrderService;
import com.monty.matchbook.gateway.api.dto.CancelOrderResponse;
import com.monty.matchbook.gateway.api.dto.OrderResponse;
import com.monty.matchbook.gateway.api.dto.SubmitOrderRequest;
import com.monty.matchbook.gateway.api.dto.SubmitOrderResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Nested
    class Submitting {

        @Test
        void aValidOrderIsAccepted() throws Exception {
            given(orderService.submitOrder(any())).willReturn(new SubmitOrderResponse(ORDER_ID, OrderStatus.NEW));

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validLimitOrder()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                    .andExpect(jsonPath("$.status").value("NEW"));
        }

        @Test
        void aValidMarketOrderIsAccepted() throws Exception {
            given(orderService.submitOrder(any())).willReturn(new SubmitOrderResponse(ORDER_ID, OrderStatus.NEW));

            String body = """
                    {"clientId":"client-1","symbol":"AAPL","side":"BUY","type":"MARKET","quantity":100}
                    """;

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted());
        }
    }

    @Nested
    class Validation {

        @Test
        void everyFailingFieldIsReportedAtOnce() throws Exception {
            String body = """
                    {"clientId":"","symbol":"AAPL","side":"BUY","type":"LIMIT","price":"1.52","quantity":0}
                    """;

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errors.clientId").exists())
                    .andExpect(jsonPath("$.errors.quantity").exists());

            verifyNoInteractions(orderService);
        }

        @Test
        void aMissingQuantityIsRejected() throws Exception {
            String body = """
                    {"clientId":"client-1","symbol":"AAPL","side":"BUY","type":"LIMIT","price":"1.52"}
                    """;

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.quantity").exists());
        }

        @Test
        void aLimitOrderWithoutAPriceIsRejected() throws Exception {
            String body = """
                    {"clientId":"client-1","symbol":"AAPL","side":"BUY","type":"LIMIT","quantity":100}
                    """;

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }

        @Test
        void aMarketOrderWithAPriceIsRejected() throws Exception {
            String body = """
                    {"clientId":"client-1","symbol":"AAPL","side":"BUY","type":"MARKET","price":"1.52","quantity":100}
                    """;

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }
    }

    @Nested
    class MalformedRequests {

        @Test
        void anUnknownEnumValueIsRejectedWithoutLeakingInternals() throws Exception {
            String body = """
                    {"clientId":"client-1","symbol":"AAPL","side":"BUYY","type":"LIMIT","price":"1.52","quantity":100}
                    """;

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Malformed request"))
                    .andExpect(
                            jsonPath("$.detail").value(Matchers.not(Matchers.containsString("com.monty.matchbook"))));

            verifyNoInteractions(orderService);
        }

        @Test
        void brokenJsonIsRejected() throws Exception {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Malformed request"));
        }
    }

    @Nested
    class InvalidPrice {

        @Test
        void aPriceOffTheTickIsRejected() throws Exception {
            given(orderService.submitOrder(any(SubmitOrderRequest.class)))
                    .willThrow(new InvalidPriceException("price 1.523 is not a whole number of ticks of 0.01", null));

            String body = """
                    {"clientId":"client-1","symbol":"AAPL","side":"BUY","type":"LIMIT","price":"1.523","quantity":100}
                    """;

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid price"));
        }
    }

    @Nested
    class Fetching {

        @Test
        void anExistingOrderIsReturnedWithADecimalPrice() throws Exception {
            given(orderService.get(ORDER_ID))
                    .willReturn(new OrderResponse(
                            ORDER_ID,
                            "client-1",
                            "AAPL",
                            Side.BUY,
                            OrderType.LIMIT,
                            new BigDecimal("1.52"),
                            100,
                            0,
                            OrderStatus.NEW));

            mockMvc.perform(get("/orders/{id}", ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(1.52))
                    .andExpect(jsonPath("$.quantity").value(100))
                    .andExpect(jsonPath("$.status").value("NEW"));
        }

        @Test
        void anUnknownOrderIsNotFound() throws Exception {
            given(orderService.get(ORDER_ID)).willThrow(new OrderNotFoundException(ORDER_ID));

            mockMvc.perform(get("/orders/{id}", ORDER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Order not found"));
        }

        @Test
        void aMalformedIdIsRejected() throws Exception {
            mockMvc.perform(get("/orders/{id}", "not-a-uuid")).andExpect(status().is4xxClientError());
        }
    }

    @Nested
    class Cancelling {

        @Test
        void cancellingAKnownOrderReturnsCancelled() throws Exception {
            given(orderService.cancelOrder(ORDER_ID))
                    .willReturn(new CancelOrderResponse(ORDER_ID, CancelResult.CANCELLED));

            mockMvc.perform(delete("/orders/{id}", ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("CANCELLED"));
        }

        @Test
        void cancellingAnUnknownOrderIsNotAnError() throws Exception {
            given(orderService.cancelOrder(ORDER_ID))
                    .willReturn(new CancelOrderResponse(ORDER_ID, CancelResult.NOT_FOUND));

            mockMvc.perform(delete("/orders/{id}", ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("NOT_FOUND"));
        }
    }

    private static String validLimitOrder() {
        return """
                {"clientId":"client-1","symbol":"AAPL","side":"BUY","type":"LIMIT","price":"1.52","quantity":100}
                """;
    }
}
