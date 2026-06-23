package com.exchange.mock.error;

/** Raised when an order or query references a symbol the exchange does not list (→ HTTP 422). */
public class UnknownSymbolException extends RuntimeException {
    public UnknownSymbolException(String message) {
        super(message);
    }
}
