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

    public boolean crosses(long incomingPrice, long restingPrice) {
        if (this == Side.BUY) {
            return incomingPrice >= restingPrice;
        }
        return incomingPrice <= restingPrice;
    }
}
