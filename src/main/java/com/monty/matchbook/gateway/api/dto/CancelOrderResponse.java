package com.monty.matchbook.gateway.api.dto;

import java.util.UUID;

public record CancelOrderResponse(UUID orderId, CancelOutcome outcome) {}
