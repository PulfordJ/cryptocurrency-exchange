package com.exchange.mock.account;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** An account's balances keyed by asset (e.g. {@code USD}, {@code BTC}). */
public class Account {

    private final String id;
    private final Map<String, Balance> balances = new LinkedHashMap<>();

    public Account(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Balance balance(String asset) {
        return balances.computeIfAbsent(asset, a -> new Balance(BigDecimal.ZERO));
    }

    public Map<String, Balance> balances() {
        return balances;
    }
}
