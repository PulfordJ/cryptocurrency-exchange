package com.exchange.mock.error;

/** Raised when a clientOrderId is reused for an account (idempotency guard → HTTP 409). */
public class DuplicateClientOrderIdException extends RuntimeException {
    public DuplicateClientOrderIdException(String message) {
        super(message);
    }
}
