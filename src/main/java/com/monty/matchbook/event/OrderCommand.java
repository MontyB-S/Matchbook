package com.monty.matchbook.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.UUID;

/**
 * Everything the gateway sends the matching component. Submits and cancels share one topic so that
 * Kafka's per-partition ordering applies between them — on separate topics a cancel could be
 * consumed before the order it cancels.
 *
 * <p>The discriminator names are fixed strings rather than class names, so these types can be
 * renamed without breaking messages already on the topic.
 */
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
