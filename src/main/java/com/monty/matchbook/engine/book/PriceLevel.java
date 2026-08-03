package com.monty.matchbook.engine.book;

import java.util.ArrayDeque;

final class PriceLevel {

    private final long priceTicks;
    private final ArrayDeque<RestingOrder> queue = new ArrayDeque<>();
    private long totalQuantity;

    PriceLevel(long priceTicks) {
        this.priceTicks = priceTicks;
    }

    void add(RestingOrder restingOrder) {
        queue.addLast(restingOrder);
        totalQuantity += restingOrder.getRemainingQuantity();
    }

    boolean remove(RestingOrder restingOrder) {
        if (!queue.remove(restingOrder)) {
            return false;
        }
        totalQuantity -= restingOrder.getRemainingQuantity();
        return true;
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    long priceTicks() {
        return priceTicks;
    }

    long totalQuantity() {
        return totalQuantity;
    }

    RestingOrder peekFirst() {
        return queue.getFirst();
    }

    void fillFront(long quantity) {
        RestingOrder front = queue.getFirst();

        front.setRemainingQuantity(front.getRemainingQuantity() - quantity);
        totalQuantity -= quantity;

        if (front.getRemainingQuantity() == 0) {
            queue.removeFirst();
        }
    }
}
