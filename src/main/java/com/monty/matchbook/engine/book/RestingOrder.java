package com.monty.matchbook.engine.book;

import com.monty.matchbook.engine.model.Order;

final class RestingOrder {

    private final Order order;

    private long remainingQuantity;

    RestingOrder(Order order) {
        this.order = order;
        this.remainingQuantity = order.quantity();
    }

    public long getRemainingQuantity() {
        return remainingQuantity;
    }

    public Order getOrder() {
        return order;
    }

    public void setRemainingQuantity(long remainingQuantity) {
        if (remainingQuantity < 0 || remainingQuantity > order.quantity()) {
            throw new IllegalStateException("remainingQuantity out of range");
        }
        this.remainingQuantity = remainingQuantity;
    }
}
