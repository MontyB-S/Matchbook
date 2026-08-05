package com.monty.matchbook.gateway.api.dto;

import com.monty.matchbook.engine.model.OrderStatus;
import java.util.UUID;

public record SubmitOrderResponse(UUID orderId, OrderStatus status) {}
