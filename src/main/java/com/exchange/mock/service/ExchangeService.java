package com.exchange.mock.service;

import com.exchange.mock.account.Account;
import com.exchange.mock.account.AccountNotFoundException;
import com.exchange.mock.account.AccountService;
import com.exchange.mock.api.OrderBookView;
import com.exchange.mock.api.PlaceOrderRequest;
import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.OrderSource;
import com.exchange.mock.domain.OrderType;
import com.exchange.mock.domain.Symbol;
import com.exchange.mock.engine.MatchingEngine;
import com.exchange.mock.engine.OrderRepository;
import com.exchange.mock.error.DuplicateClientOrderIdException;
import com.exchange.mock.error.OrderNotFoundException;
import com.exchange.mock.error.OrderValidationException;
import com.exchange.mock.error.UnknownSymbolException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Protocol-agnostic entry point to the exchange. REST, WebSocket and FIX all funnel through here,
 * so order validation, idempotency and state stay identical across transports.
 */
@Service
public class ExchangeService {

    /** Instruments the mock lists. Kept small and fixed to keep the mock deterministic. */
    private static final Set<String> SUPPORTED_SYMBOLS = Set.of("BTC-USD", "ETH-USD", "ETH-BTC");

    private final MatchingEngine engine;
    private final AccountService accounts;
    private final OrderRepository repository;

    private final Set<String> seenClientOrderIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AtomicLong orderSequence = new AtomicLong();

    public ExchangeService(MatchingEngine engine, AccountService accounts, OrderRepository repository) {
        this.engine = engine;
        this.accounts = accounts;
        this.repository = repository;
    }

    public Set<String> supportedSymbols() {
        return SUPPORTED_SYMBOLS;
    }

    /** Validate, fund and submit an order from any protocol. */
    public Order placeOrder(PlaceOrderRequest request, OrderSource source) {
        if (!SUPPORTED_SYMBOLS.contains(request.symbol())) {
            throw new UnknownSymbolException("symbol not listed: " + request.symbol());
        }
        if (!accounts.exists(request.accountId())) {
            throw new AccountNotFoundException("unknown account: " + request.accountId());
        }
        if (request.type() == OrderType.LIMIT && request.price() == null) {
            throw new OrderValidationException("limit orders require a price");
        }

        String clientOrderId = request.clientOrderId();
        if (clientOrderId != null && !clientOrderId.isBlank()) {
            String key = request.accountId() + '|' + clientOrderId;
            if (!seenClientOrderIds.add(key)) {
                throw new DuplicateClientOrderIdException(
                        "clientOrderId already used for account " + request.accountId() + ": " + clientOrderId);
            }
        }

        BigDecimal price = request.type() == OrderType.LIMIT ? request.price() : null;
        Order order = new Order(
                "ORD-" + orderSequence.incrementAndGet(),
                clientOrderId,
                request.accountId(),
                Symbol.parse(request.symbol()),
                request.side(),
                request.type(),
                price,
                request.quantity(),
                source,
                Instant.now());

        return engine.submit(order);
    }

    public Order cancelOrder(String orderId) {
        return engine.cancel(orderId);
    }

    public Order getOrder(String orderId) {
        return repository.find(orderId)
                .orElseThrow(() -> new OrderNotFoundException("unknown order: " + orderId));
    }

    public List<Order> listOrders(String accountId) {
        return repository.all().stream()
                .filter(o -> accountId == null || o.accountId().equals(accountId))
                .toList();
    }

    public OrderBookView orderBook(String symbol, int depth) {
        if (!SUPPORTED_SYMBOLS.contains(symbol)) {
            throw new UnknownSymbolException("symbol not listed: " + symbol);
        }
        return engine.snapshot(Symbol.parse(symbol), depth);
    }

    public Account account(String accountId) {
        return accounts.account(accountId);
    }

    /** Reset all exchange state — used to isolate integration tests. */
    public void reset() {
        accounts.reset();
        repository.reset();
        engine.reset();
        seenClientOrderIds.clear();
        orderSequence.set(0);
    }
}
