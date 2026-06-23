package com.exchange.mock.api;

import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.OrderStatus;
import com.exchange.mock.domain.OrderType;
import com.exchange.mock.domain.Side;
import java.math.BigDecimal;
import java.time.Instant;

/** Immutable snapshot of an order's state, used for REST responses and order-event payloads. */
public record OrderView(
        String orderId,
        String clientOrderId,
        String accountId,
        String symbol,
        Side side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        OrderStatus status,
        String rejectReason,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderView of(Order o) {
        return new OrderView(
                o.id(),
                o.clientOrderId(),
                o.accountId(),
                o.symbol().name(),
                o.side(),
                o.type(),
                o.price(),
                o.quantity(),
                o.filledQuantity(),
                o.remainingQuantity(),
                o.status(),
                o.rejectReason(),
                o.createdAt(),
                o.updatedAt());
    }
}
