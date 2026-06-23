package com.exchange.mock.api;

import com.exchange.mock.account.Account;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable snapshot of an account's balances. */
public record AccountView(String accountId, Map<String, BalanceView> balances) {

    public record BalanceView(BigDecimal available, BigDecimal reserved, BigDecimal total) {
    }

    public static AccountView of(Account account) {
        Map<String, BalanceView> balances = new LinkedHashMap<>();
        account.balances().forEach((asset, b) ->
                balances.put(asset, new BalanceView(b.available(), b.reserved(), b.total())));
        return new AccountView(account.id(), balances);
    }
}
