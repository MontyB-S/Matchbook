package com.monty.matchbook.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderAccepted.class, name = "ORDER_ACCEPTED"),
    @JsonSubTypes.Type(value = OrderCancelled.class, name = "ORDER_CANCELLED")
})
public sealed interface OrderCommand permits OrderAccepted, OrderCancelled {

    UUID eventId();

    String symbol();

    Instant occurredAt();
}
