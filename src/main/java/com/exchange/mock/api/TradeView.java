package com.exchange.mock.api;

import com.exchange.mock.domain.Side;
import com.exchange.mock.domain.Trade;
import java.math.BigDecimal;
import java.time.Instant;

/** Immutable snapshot of an executed trade. */
public record TradeView(
        String tradeId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        String buyOrderId,
        String sellOrderId,
        Side aggressorSide,
        Instant executedAt) {

    public static TradeView of(Trade t) {
        return new TradeView(
                t.id(),
                t.symbol().name(),
                t.price(),
                t.quantity(),
                t.buyOrderId(),
                t.sellOrderId(),
                t.aggressorSide(),
                t.executedAt());
    }
}
