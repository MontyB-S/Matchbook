package com.monty.matchbook.engine.model;

public enum OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED;

    public boolean isFinished() {
        return this == FILLED || this == CANCELLED || this == REJECTED;
    }
}
