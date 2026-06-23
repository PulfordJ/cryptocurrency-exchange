package com.exchange.mock.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Aggregated top-of-book view: bids sorted high→low, asks sorted low→high. */
public record OrderBookView(
        String symbol,
        List<PriceLevel> bids,
        List<PriceLevel> asks,
        Instant timestamp) {

    /** One aggregated price level: total resting quantity and order count at a price. */
    public record PriceLevel(BigDecimal price, BigDecimal quantity, int orderCount) {
    }
}
