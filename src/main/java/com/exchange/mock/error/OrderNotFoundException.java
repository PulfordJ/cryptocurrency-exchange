package com.exchange.mock.error;

/** Raised when an order id cannot be found (→ HTTP 404). */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
