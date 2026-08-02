package com.monty.matchbook.engine.model;

public enum Side {
    BUY,
    SELL;

    private Side opposite;

    static {
        BUY.opposite = SELL;
        SELL.opposite = BUY;
    }

    public Side opposite() {
        return opposite;
    }
}
