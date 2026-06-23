package com.exchange.mock.domain;

import java.util.Set;

/** Lifecycle states of an order. */
public enum OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED;

    private static final Set<OrderStatus> TERMINAL = Set.of(FILLED, CANCELLED, REJECTED);

    /** Terminal states cannot transition further (e.g. a filled order cannot be cancelled). */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
