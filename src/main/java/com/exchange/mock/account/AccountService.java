package com.exchange.mock.account;

import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.OrderType;
import com.exchange.mock.domain.Side;
import com.exchange.mock.domain.Symbol;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

/**
 * Tracks per-account asset balances and performs the funding side of trading: reserving funds when
 * a (limit) order is accepted, releasing them on cancel, and settling both sides of a trade with
 * exact {@link BigDecimal} double-entry bookkeeping.
 *
 * <p>All mutating operations take a single service-wide lock. That is conservative for a mock but
 * keeps the funding logic obviously correct and free of cross-account deadlocks.
 *
 * <p>Funding rules:
 * <ul>
 *   <li><b>Limit buy</b> reserves {@code price × quantity} of the quote asset.</li>
 *   <li><b>Limit sell</b> reserves {@code quantity} of the base asset.</li>
 *   <li><b>Market</b> orders are not reserved up-front (no price); they settle from available
 *       balance at execution.</li>
 *   <li>Trades print at the maker price, so an aggressive limit order is refunded any price
 *       improvement back to its available balance.</li>
 * </ul>
 */
@Service
public class AccountService {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public AccountService() {
        seed();
    }

    /** Restore the seeded demo accounts; used to isolate tests. */
    public void reset() {
        lock.lock();
        try {
            accounts.clear();
            seed();
        } finally {
            lock.unlock();
        }
    }

    private void seed() {
        seedAccount("ACC-1", Map.of("USD", "1000000", "BTC", "100", "ETH", "1000"));
        seedAccount("ACC-2", Map.of("USD", "1000000", "BTC", "100", "ETH", "1000"));
        // Deliberately unfunded — drives insufficient-funds rejection tests.
        seedAccount("ACC-EMPTY", Map.of("USD", "0", "BTC", "0"));
    }

    private void seedAccount(String id, Map<String, String> openingBalances) {
        Account account = new Account(id);
        openingBalances.forEach((asset, amount) ->
                account.balances().put(asset, new Balance(new BigDecimal(amount))));
        accounts.put(id, account);
    }

    public boolean exists(String accountId) {
        return accounts.containsKey(accountId);
    }

    private Account require(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException("unknown account: " + accountId);
        }
        return account;
    }

    /** Read-only snapshot of an account's balances. Throws if the account is unknown. */
    public Account account(String accountId) {
        lock.lock();
        try {
            return require(accountId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reserve the funds an order needs. No-op for market orders. Throws
     * {@link InsufficientFundsException} (leaving balances untouched) if the available balance is
     * too low.
     */
    public void reserve(Order order) {
        if (order.type() == OrderType.MARKET) {
            return;
        }
        lock.lock();
        try {
            Account account = require(order.accountId());
            Symbol symbol = order.symbol();
            String asset = order.side() == Side.BUY ? symbol.quote() : symbol.base();
            BigDecimal required = order.side() == Side.BUY
                    ? order.price().multiply(order.quantity())
                    : order.quantity();
            Balance balance = account.balance(asset);
            if (balance.available().compareTo(required) < 0) {
                throw new InsufficientFundsException(
                        "account %s has %s %s available but order requires %s"
                                .formatted(order.accountId(), balance.available(), asset, required));
            }
            balance.debitAvailable(required);
            balance.creditReserved(required);
            order.reserve(asset, required);
        } finally {
            lock.unlock();
        }
    }

    /** Release any funds still reserved for an order (on cancellation). */
    public void releaseRemaining(Order order) {
        lock.lock();
        try {
            BigDecimal remaining = order.reservedAmount();
            if (order.reservedAsset() == null || remaining.signum() <= 0) {
                return;
            }
            Balance balance = require(order.accountId()).balance(order.reservedAsset());
            balance.debitReserved(remaining);
            balance.creditAvailable(remaining);
            order.consumeReserved(remaining);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Settle one executed trade between a buy and a sell order: buyer pays quote and receives base,
     * seller pays base and receives quote. Reserved funds are drawn down for limit orders; market
     * orders are debited from available balance.
     */
    public void settleFill(Order buy, Order sell, BigDecimal tradePrice, BigDecimal quantity) {
        lock.lock();
        try {
            Symbol symbol = buy.symbol();
            BigDecimal quoteAmount = tradePrice.multiply(quantity);

            Account buyer = require(buy.accountId());
            Balance buyerQuote = buyer.balance(symbol.quote());
            if (buy.type() == OrderType.LIMIT) {
                // Reserved at the limit price; refund any improvement vs. the maker price.
                BigDecimal reservedConsumed = buy.price().multiply(quantity);
                buyerQuote.debitReserved(reservedConsumed);
                BigDecimal improvement = reservedConsumed.subtract(quoteAmount);
                if (improvement.signum() != 0) {
                    buyerQuote.creditAvailable(improvement);
                }
                buy.consumeReserved(reservedConsumed);
            } else {
                buyerQuote.debitAvailable(quoteAmount);
            }
            buyer.balance(symbol.base()).creditAvailable(quantity);

            Account seller = require(sell.accountId());
            Balance sellerBase = seller.balance(symbol.base());
            if (sell.type() == OrderType.LIMIT) {
                sellerBase.debitReserved(quantity);
                sell.consumeReserved(quantity);
            } else {
                sellerBase.debitAvailable(quantity);
            }
            seller.balance(symbol.quote()).creditAvailable(quoteAmount);
        } finally {
            lock.unlock();
        }
    }
}
