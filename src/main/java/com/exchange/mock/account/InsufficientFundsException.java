package com.exchange.mock.account;

/** Raised when an account lacks the available balance required to back an order. */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
