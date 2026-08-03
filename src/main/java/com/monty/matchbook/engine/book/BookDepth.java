package com.monty.matchbook.engine.book;

import java.util.List;

public record BookDepth(List<Level> bids, List<Level> asks) {
    public BookDepth {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}
