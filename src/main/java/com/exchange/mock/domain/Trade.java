package com.exchange.mock.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An executed match between a resting (maker) order and an incoming (taker) order. Trades always
 * print at the maker's price (standard price-time priority), which may improve the taker's price.
 */
public record Trade(
        String id,
        Symbol symbol,
        BigDecimal price,
        BigDecimal quantity,
        String buyOrderId,
        String sellOrderId,
        Side aggressorSide,
        Instant executedAt) {
}
