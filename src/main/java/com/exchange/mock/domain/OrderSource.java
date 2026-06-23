package com.exchange.mock.domain;

/** Which protocol an order entered through. Lets each transport react only to its own orders. */
public enum OrderSource {
    REST,
    FIX
}
