package com.exchange.mock.account;

/** Raised when an order or query references an account that the exchange does not know about. */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
