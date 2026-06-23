package com.exchange.mock.error;

/** Raised for business validation failures not caught by bean validation (→ HTTP 400). */
public class OrderValidationException extends RuntimeException {
    public OrderValidationException(String message) {
        super(message);
    }
}
