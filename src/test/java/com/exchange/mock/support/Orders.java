package com.exchange.mock.support;

import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.OrderSource;
import com.exchange.mock.domain.OrderType;
import com.exchange.mock.domain.Side;
import com.exchange.mock.domain.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builders for domain {@link Order} objects used by the unit tests that exercise the matching
 * engine, order book and funding directly (without booting Spring). Mirrors the id/timestamp shape
 * the production {@code ExchangeService} assigns; each call gets a unique id and a fresh timestamp,
 * so insertion order doubles as time priority.
 */
public final class Orders {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private Orders() {
    }

    public static Order limit(String accountId, String symbol, Side side, Object price, Object quantity) {
        return order(accountId, symbol, side, OrderType.LIMIT, bd(price), bd(quantity));
    }

    public static Order market(String accountId, String symbol, Side side, Object quantity) {
        return order(accountId, symbol, side, OrderType.MARKET, null, bd(quantity));
    }

    public static Order order(String accountId, String symbol, Side side, OrderType type,
                              BigDecimal price, BigDecimal quantity) {
        return new Order("ORD-" + SEQUENCE.incrementAndGet(), null, accountId, Symbol.parse(symbol),
                side, type, price, quantity, OrderSource.REST, Instant.now());
    }

    private static BigDecimal bd(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }
}
