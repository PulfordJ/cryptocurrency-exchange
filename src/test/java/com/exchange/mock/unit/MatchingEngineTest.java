package com.exchange.mock.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.exchange.mock.account.AccountService;
import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.OrderStatus;
import com.exchange.mock.domain.Side;
import com.exchange.mock.domain.Symbol;
import com.exchange.mock.engine.MatchingEngine;
import com.exchange.mock.engine.OrderRepository;
import com.exchange.mock.support.Orders;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link MatchingEngine}, wired with a real {@link AccountService} and
 * {@link OrderRepository} and a capturing event publisher. These pin engine semantics — including
 * deliberate edge cases — directly on returned order state and balances, without booting Spring.
 */
class MatchingEngineTest {

    private final AccountService accounts = new AccountService();
    private final OrderRepository repository = new OrderRepository();
    private final List<Object> events = new ArrayList<>();
    private final MatchingEngine engine = new MatchingEngine(accounts, repository, events::add);

    @Test
    @DisplayName("self-trade is not prevented: an account can match its own resting order")
    void selfTradeIsNotPrevented() {
        engine.submit(Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, 1));
        Order buy = engine.submit(Orders.limit("ACC-1", "BTC-USD", Side.BUY, 30000, 1));

        assertThat(buy.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("cancelling a partially-filled limit releases only the remaining reserve")
    void partialFillThenCancelReleasesRemainingReserve() {
        Order resting = engine.submit(Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, 1));
        engine.submit(Orders.limit("ACC-2", "BTC-USD", Side.BUY, 30000, "0.4"));

        assertThat(resting.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(reserved("ACC-1", "BTC")).isEqualByComparingTo("0.6");

        engine.cancel(resting.id());

        // The 0.4 sold is gone; the unsold 0.6 returns to available, nothing left reserved.
        assertThat(resting.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(available("ACC-1", "BTC")).isEqualByComparingTo("99.6");
        assertThat(reserved("ACC-1", "BTC")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a market order that exhausts the book keeps what it filled and drops the remainder")
    void marketPartialFillDropsRemainder() {
        engine.submit(Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, "0.5"));

        Order market = engine.submit(Orders.market("ACC-2", "BTC-USD", Side.BUY, 1));

        // Current behaviour: a partially-filled market remainder is neither rested nor rejected.
        assertThat(market.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(market.filledQuantity()).isEqualByComparingTo("0.5");
        assertThat(market.remainingQuantity()).isEqualByComparingTo("0.5");
        assertThat(engine.snapshot(Symbol.parse("BTC-USD"), 10).asks()).isEmpty();
    }

    @Test
    @DisplayName("a market order with no liquidity is rejected")
    void marketOrderNoLiquidityRejected() {
        Order market = engine.submit(Orders.market("ACC-2", "BTC-USD", Side.BUY, 1));

        assertThat(market.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(market.rejectReason()).isEqualTo("no liquidity available for market order");
    }

    private java.math.BigDecimal available(String accountId, String asset) {
        return accounts.account(accountId).balance(asset).available();
    }

    private java.math.BigDecimal reserved(String accountId, String asset) {
        return accounts.account(accountId).balance(asset).reserved();
    }
}
