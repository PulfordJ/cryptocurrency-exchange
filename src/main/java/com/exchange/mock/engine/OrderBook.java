package com.exchange.mock.engine;

import com.exchange.mock.api.OrderBookView;
import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.Side;
import com.exchange.mock.domain.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A single symbol's limit order book. Each side is a price-ordered map of FIFO queues, giving
 * price-time priority: bids are visited highest-price-first, asks lowest-price-first, and within a
 * price level the earliest order matches first.
 *
 * <p>Not internally synchronized — the {@link MatchingEngine} guards each book with a per-symbol
 * lock.
 */
public class OrderBook {

    private final Symbol symbol;
    private final NavigableMap<BigDecimal, Deque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, Deque<Order>> asks = new TreeMap<>();

    public OrderBook(Symbol symbol) {
        this.symbol = symbol;
    }

    public Symbol symbol() {
        return symbol;
    }

    /** The book to match an incoming order against (a buy matches the asks, a sell the bids). */
    public NavigableMap<BigDecimal, Deque<Order>> oppositeSide(Side incomingSide) {
        return incomingSide == Side.BUY ? asks : bids;
    }

    /** Rest a limit order on its own side of the book. */
    public void rest(Order order) {
        sideFor(order.side())
                .computeIfAbsent(order.price(), p -> new ArrayDeque<>())
                .addLast(order);
    }

    /** Remove a resting order (e.g. on cancellation); no-op if it is not on the book. */
    public void remove(Order order) {
        if (order.price() == null) {
            return;
        }
        NavigableMap<BigDecimal, Deque<Order>> side = sideFor(order.side());
        Deque<Order> level = side.get(order.price());
        if (level != null) {
            level.removeIf(o -> o.id().equals(order.id()));
            if (level.isEmpty()) {
                side.remove(order.price());
            }
        }
    }

    private NavigableMap<BigDecimal, Deque<Order>> sideFor(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    /** Aggregated snapshot of up to {@code depth} price levels per side. */
    public OrderBookView snapshot(int depth) {
        return new OrderBookView(symbol.name(), aggregate(bids, depth), aggregate(asks, depth), Instant.now());
    }

    private static List<OrderBookView.PriceLevel> aggregate(
            NavigableMap<BigDecimal, Deque<Order>> side, int depth) {
        List<OrderBookView.PriceLevel> levels = new ArrayList<>();
        for (Map.Entry<BigDecimal, Deque<Order>> entry : side.entrySet()) {
            if (levels.size() == depth) {
                break;
            }
            BigDecimal totalQty = entry.getValue().stream()
                    .map(Order::remainingQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            levels.add(new OrderBookView.PriceLevel(entry.getKey(), totalQty, entry.getValue().size()));
        }
        return levels;
    }
}
