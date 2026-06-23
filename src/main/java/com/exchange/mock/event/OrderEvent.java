package com.exchange.mock.event;

import com.exchange.mock.api.OrderView;
import com.exchange.mock.api.TradeView;
import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.OrderSource;
import com.exchange.mock.domain.Trade;
import com.exchange.mock.domain.OrderStatus;
import java.time.Instant;

/**
 * An order-lifecycle event published by the matching engine and consumed by the WebSocket order
 * stream and the FIX acceptor. Carries an immutable {@link OrderView} snapshot (the live order may
 * mutate further) plus the originating {@link OrderSource} so each transport reacts only to its own
 * orders.
 */
public record OrderEvent(
        OrderEventType type,
        OrderSource source,
        OrderView order,
        TradeView trade,
        Instant at) {

    public static OrderEvent accepted(Order order) {
        return new OrderEvent(OrderEventType.ACCEPTED, order.source(), OrderView.of(order), null, Instant.now());
    }

    public static OrderEvent rejected(Order order) {
        return new OrderEvent(OrderEventType.REJECTED, order.source(), OrderView.of(order), null, Instant.now());
    }

    public static OrderEvent cancelled(Order order) {
        return new OrderEvent(OrderEventType.CANCELLED, order.source(), OrderView.of(order), null, Instant.now());
    }

    /** A fill on the given order; the resulting status decides PARTIALLY_FILLED vs FILLED. */
    public static OrderEvent fill(Order order, Trade trade) {
        OrderEventType type = order.status() == OrderStatus.FILLED
                ? OrderEventType.FILLED
                : OrderEventType.PARTIALLY_FILLED;
        return new OrderEvent(type, order.source(), OrderView.of(order), TradeView.of(trade), Instant.now());
    }
}
