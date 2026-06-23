package com.exchange.mock.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builders for order request JSON bodies, keeping the tests readable. */
public final class OrderRequests {

    private OrderRequests() {
    }

    public static Map<String, Object> limit(String accountId, String symbol, String side,
                                             Object price, Object quantity) {
        Map<String, Object> body = base(accountId, symbol, side, "LIMIT", quantity);
        body.put("price", price);
        return body;
    }

    public static Map<String, Object> market(String accountId, String symbol, String side,
                                              Object quantity) {
        return base(accountId, symbol, side, "MARKET", quantity);
    }

    private static Map<String, Object> base(String accountId, String symbol, String side,
                                            String type, Object quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", accountId);
        body.put("symbol", symbol);
        body.put("side", side);
        body.put("type", type);
        body.put("quantity", quantity);
        return body;
    }

    /** Fluently attach a clientOrderId to a request body. */
    public static Map<String, Object> withClientOrderId(Map<String, Object> body, String clientOrderId) {
        body.put("clientOrderId", clientOrderId);
        return body;
    }
}
