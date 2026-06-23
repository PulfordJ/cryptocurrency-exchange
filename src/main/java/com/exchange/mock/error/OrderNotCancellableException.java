package com.exchange.mock.error;

/** Raised when cancelling an order that is already in a terminal state (→ HTTP 409). */
public class OrderNotCancellableException extends RuntimeException {
    public OrderNotCancellableException(String message) {
        super(message);
    }
}
