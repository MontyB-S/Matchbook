package com.monty.matchbook.gateway.domain;

import com.monty.matchbook.engine.model.OrderStatus;
import com.monty.matchbook.engine.model.OrderType;
import com.monty.matchbook.engine.model.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType type;

    @Column(name = "price_ticks")
    private Long priceTicks;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private long filledQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Version
    private long version;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected OrderEntity() {}

    public OrderEntity(
            UUID id,
            String idempotencyKey,
            String clientId,
            String symbol,
            Side side,
            OrderType type,
            Long priceTicks,
            long quantity) {

        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.clientId = clientId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.priceTicks = priceTicks;
        this.quantity = quantity;
        this.filledQuantity = 0;
        this.status = OrderStatus.NEW;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Side getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public Long getPriceTicks() {
        return priceTicks;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getFilledQuantity() {
        return filledQuantity;
    }

    public void setFilledQuantity(long filledQuantity) {
        this.filledQuantity = filledQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
