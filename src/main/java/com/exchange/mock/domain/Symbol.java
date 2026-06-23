package com.exchange.mock.domain;

/**
 * A trading instrument, e.g. {@code BTC-USD}: buy/sell the {@code base} asset (BTC),
 * paying/receiving in the {@code quote} asset (USD).
 */
public record Symbol(String name, String base, String quote) {

    public static Symbol parse(String name) {
        if (name == null) {
            throw new IllegalArgumentException("symbol must not be null");
        }
        String[] parts = name.split("-");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("symbol must be of the form BASE-QUOTE, got: " + name);
        }
        return new Symbol(name, parts[0], parts[1]);
    }

    @Override
    public String toString() {
        return name;
    }
}
