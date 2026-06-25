package com.exchange.mock.account;

import java.math.BigDecimal;

/**
 * A single asset balance split into {@code available} (free to trade) and {@code reserved} (held
 * against working orders). Mutated only under the {@link AccountService} lock.
 */
public class Balance {

    private BigDecimal available;
    private BigDecimal reserved;

    public Balance(BigDecimal available) {
        this.available = available;
        this.reserved = BigDecimal.ZERO;
    }

    public BigDecimal available() {
        return available;
    }

    public BigDecimal reserved() {
        return reserved;
    }

    public BigDecimal total() {
        return available.add(reserved);
    }

    void creditAvailable(BigDecimal amount) {
        available = available.add(amount);
    }

    void debitAvailable(BigDecimal amount) {
        available = available.subtract(amount);
    }

    void creditReserved(BigDecimal amount) {
        reserved = reserved.add(amount);
    }

    void debitReserved(BigDecimal amount) {
        reserved = reserved.subtract(amount);
    }
}
