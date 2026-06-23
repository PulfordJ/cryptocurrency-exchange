package com.exchange.mock.engine;

import com.exchange.mock.account.AccountService;
import com.exchange.mock.account.InsufficientFundsException;
import com.exchange.mock.api.OrderBookView;
import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.OrderType;
import com.exchange.mock.domain.Side;
import com.exchange.mock.domain.Symbol;
import com.exchange.mock.domain.Trade;
import com.exchange.mock.error.OrderNotCancellableException;
import com.exchange.mock.error.OrderNotFoundException;
import com.exchange.mock.event.BookChangedEvent;
import com.exchange.mock.event.OrderEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The core matching engine: one {@link OrderBook} per symbol, guarded by a per-symbol lock.
 * Incoming orders are funded, matched against the opposite side with price-time priority, and any
 * limit remainder is rested. Market remainders are cancelled (immediate-or-cancel).
 *
 * <p>State mutation happens under the symbol lock; lifecycle events are buffered and published only
 * after the lock is released, so transports (WebSocket/FIX) never run I/O while the book is locked.
 */
@Component
public class MatchingEngine {

    public static final int DEFAULT_DEPTH = 10;

    private final AccountService accounts;
    private final OrderRepository repository;
    private final ApplicationEventPublisher events;

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong tradeSequence = new AtomicLong();

    public MatchingEngine(AccountService accounts, OrderRepository repository,
                          ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.repository = repository;
        this.events = events;
    }

    /** Submit a new order: fund, match, rest/cancel remainder. Returns the order's final state. */
    public Order submit(Order order) {
        String symbol = order.symbol().name();
        ReentrantLock lock = lockFor(symbol);
        List<Object> pending = new ArrayList<>();
        lock.lock();
        try {
            try {
                accounts.reserve(order);
            } catch (InsufficientFundsException e) {
                order.markRejected(e.getMessage(), Instant.now());
                repository.save(order);
                pending.add(OrderEvent.rejected(order));
                return order;
            }

            repository.save(order);
            pending.add(OrderEvent.accepted(order));

            OrderBook book = bookFor(order.symbol());
            match(order, book, pending);

            if (order.type() == OrderType.LIMIT && !order.isFullyFilled()) {
                book.rest(order);
            } else if (order.type() == OrderType.MARKET && !order.isFullyFilled()
                    && order.filledQuantity().signum() == 0) {
                // Market order with no liquidity to take: reject it.
                order.markRejected("no liquidity available for market order", Instant.now());
                pending.add(OrderEvent.rejected(order));
            }
            pending.add(new BookChangedEvent(symbol));
            return order;
        } finally {
            lock.unlock();
            publish(pending);
        }
    }

    // No self-trade prevention (STP): a client that relies on the exchange to prevent self-trades
    // rather than managing it client-side has a bug; the mock should not mask that.
    private void match(Order incoming, OrderBook book, List<Object> pending) {
        NavigableMap<BigDecimal, Deque<Order>> opposite = book.oppositeSide(incoming.side());
        while (!incoming.isFullyFilled()) {
            Map.Entry<BigDecimal, Deque<Order>> best = opposite.firstEntry();
            if (best == null || !crosses(incoming, best.getKey())) {
                break;
            }
            BigDecimal makerPrice = best.getKey();
            Deque<Order> level = best.getValue();
            while (!incoming.isFullyFilled() && !level.isEmpty()) {
                Order maker = level.peekFirst();
                BigDecimal qty = min(incoming.remainingQuantity(), maker.remainingQuantity());
                Instant now = Instant.now();

                Order buy = incoming.side() == Side.BUY ? incoming : maker;
                Order sell = incoming.side() == Side.SELL ? incoming : maker;
                Trade trade = new Trade("T-" + tradeSequence.incrementAndGet(), book.symbol(),
                        makerPrice, qty, buy.id(), sell.id(), incoming.side(), now);

                maker.recordFill(qty, now);
                incoming.recordFill(qty, now);
                accounts.settleFill(buy, sell, makerPrice, qty);
                repository.saveTrade(trade);

                pending.add(OrderEvent.fill(maker, trade));
                pending.add(OrderEvent.fill(incoming, trade));

                if (maker.isFullyFilled()) {
                    level.pollFirst();
                }
            }
            if (level.isEmpty()) {
                opposite.remove(makerPrice);
            }
        }
    }

    private static boolean crosses(Order incoming, BigDecimal makerPrice) {
        if (incoming.type() == OrderType.MARKET) {
            return true;
        }
        return incoming.side() == Side.BUY
                ? incoming.price().compareTo(makerPrice) >= 0
                : incoming.price().compareTo(makerPrice) <= 0;
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** Cancel a working order, releasing reserved funds. */
    public Order cancel(String orderId) {
        Order order = repository.find(orderId)
                .orElseThrow(() -> new OrderNotFoundException("unknown order: " + orderId));
        String symbol = order.symbol().name();
        ReentrantLock lock = lockFor(symbol);
        List<Object> pending = new ArrayList<>();
        lock.lock();
        try {
            if (order.status().isTerminal()) {
                throw new OrderNotCancellableException(
                        "order %s is %s and cannot be cancelled".formatted(orderId, order.status()));
            }
            bookFor(order.symbol()).remove(order);
            accounts.releaseRemaining(order);
            order.markCancelled(Instant.now());
            pending.add(OrderEvent.cancelled(order));
            pending.add(new BookChangedEvent(symbol));
            return order;
        } finally {
            lock.unlock();
            publish(pending);
        }
    }

    public OrderBookView snapshot(Symbol symbol, int depth) {
        ReentrantLock lock = lockFor(symbol.name());
        lock.lock();
        try {
            return bookFor(symbol).snapshot(depth);
        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        books.clear();
        tradeSequence.set(0);
    }

    private void publish(List<Object> pending) {
        for (Object event : pending) {
            events.publishEvent(event);
        }
    }

    private OrderBook bookFor(Symbol symbol) {
        return books.computeIfAbsent(symbol.name(), n -> new OrderBook(symbol));
    }

    private ReentrantLock lockFor(String symbol) {
        return locks.computeIfAbsent(symbol, s -> new ReentrantLock());
    }
}
