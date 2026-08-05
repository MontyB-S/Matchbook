package com.monty.matchbook.gateway.api.dto;

import com.monty.matchbook.engine.book.CancelResult;
import java.util.UUID;

public record CancelOrderResponse(UUID orderId, CancelResult result) {}
