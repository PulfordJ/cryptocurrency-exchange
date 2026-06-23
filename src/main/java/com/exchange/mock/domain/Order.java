package com.exchange.mock.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A working order. Mutable: the matching engine updates {@code filledQuantity}/{@code status} as
 * the order executes. All mutation happens under the owning {@link com.exchange.mock.engine.OrderBook}
 * lock, so individual orders need no internal synchronization.
 *
 * <p>Monetary and quantity fields use {@link BigDecimal} throughout — never {@code double} — so that
 * prices and balances are exact, as required for financial software.
 */
public class Order {

    private final String id;
    private final String clientOrderId;
    private final String accountId;
    private final Symbol symbol;
    private final Side side;
    private final OrderType type;
    private final BigDecimal price; // null for MARKET orders
    private final BigDecimal quantity;
    private final OrderSource source;
    private final Instant createdAt;

    private BigDecimal filledQuantity = BigDecimal.ZERO;
    private OrderStatus status = OrderStatus.NEW;
    private String rejectReason;
    private Instant updatedAt;

    // Remaining funds reserved for this order (released on cancel / consumed on fill).
    private String reservedAsset;
    private BigDecimal reservedAmount = BigDecimal.ZERO;

    public Order(String id, String clientOrderId, String accountId, Symbol symbol, Side side,
                 OrderType type, BigDecimal price, BigDecimal quantity, OrderSource source,
                 Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.clientOrderId = clientOrderId;
        this.accountId = Objects.requireNonNull(accountId);
        this.symbol = Objects.requireNonNull(symbol);
        this.side = Objects.requireNonNull(side);
        this.type = Objects.requireNonNull(type);
        this.price = price;
        this.quantity = Objects.requireNonNull(quantity);
        this.source = Objects.requireNonNull(source);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    public BigDecimal remainingQuantity() {
        return quantity.subtract(filledQuantity);
    }

    public boolean isFullyFilled() {
        return remainingQuantity().signum() == 0;
    }

    /** Apply an executed quantity, advancing status to PARTIALLY_FILLED or FILLED. */
    public void recordFill(BigDecimal executedQty, Instant when) {
        this.filledQuantity = this.filledQuantity.add(executedQty);
        this.status = isFullyFilled() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        this.updatedAt = when;
    }

    public void markCancelled(Instant when) {
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = when;
    }

    public void markRejected(String reason, Instant when) {
        this.status = OrderStatus.REJECTED;
        this.rejectReason = reason;
        this.updatedAt = when;
    }

    public void reserve(String asset, BigDecimal amount) {
        this.reservedAsset = asset;
        this.reservedAmount = amount;
    }

    public void consumeReserved(BigDecimal amount) {
        this.reservedAmount = this.reservedAmount.subtract(amount);
    }

    public String id() { return id; }
    public String clientOrderId() { return clientOrderId; }
    public String accountId() { return accountId; }
    public Symbol symbol() { return symbol; }
    public Side side() { return side; }
    public OrderType type() { return type; }
    public BigDecimal price() { return price; }
    public BigDecimal quantity() { return quantity; }
    public OrderSource source() { return source; }
    public Instant createdAt() { return createdAt; }
    public BigDecimal filledQuantity() { return filledQuantity; }
    public OrderStatus status() { return status; }
    public String rejectReason() { return rejectReason; }
    public Instant updatedAt() { return updatedAt; }
    public String reservedAsset() { return reservedAsset; }
    public BigDecimal reservedAmount() { return reservedAmount; }
}
