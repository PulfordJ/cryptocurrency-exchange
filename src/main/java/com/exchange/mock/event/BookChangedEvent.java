package com.exchange.mock.event;

/** Signals that a symbol's order book changed and market-data subscribers should be refreshed. */
public record BookChangedEvent(String symbol) {
}
