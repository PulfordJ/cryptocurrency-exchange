package com.exchange.mock.domain;

/** Side of an order: buying or selling the base asset. */
public enum Side {
    BUY,
    SELL;

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
