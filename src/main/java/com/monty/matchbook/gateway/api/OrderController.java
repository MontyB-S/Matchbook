package com.monty.matchbook.gateway.api;

import com.monty.matchbook.gateway.OrderService;
import com.monty.matchbook.gateway.api.dto.CancelOrderResponse;
import com.monty.matchbook.gateway.api.dto.OrderResponse;
import com.monty.matchbook.gateway.api.dto.SubmitOrderRequest;
import com.monty.matchbook.gateway.api.dto.SubmitOrderResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    SubmitOrderResponse submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody SubmitOrderRequest request) {
        return orderService.submitOrder(idempotencyKey, request);
    }

    @DeleteMapping("/{orderId}")
    CancelOrderResponse cancel(@PathVariable UUID orderId) {
        return orderService.cancelOrder(orderId);
    }

    @GetMapping("/{orderId}")
    OrderResponse get(@PathVariable UUID orderId) {
        return orderService.get(orderId);
    }
}
