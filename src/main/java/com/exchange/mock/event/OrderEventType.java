package com.exchange.mock.event;

/** The kind of order-lifecycle transition an {@link OrderEvent} represents. */
public enum OrderEventType {
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED
}
