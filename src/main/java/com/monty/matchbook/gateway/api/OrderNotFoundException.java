package com.monty.matchbook.gateway.api;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("no order with id " + orderId);
    }
}
