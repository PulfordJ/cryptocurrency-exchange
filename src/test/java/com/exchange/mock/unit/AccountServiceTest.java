package com.exchange.mock.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exchange.mock.account.AccountService;
import com.exchange.mock.account.InsufficientFundsException;
import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.Side;
import com.exchange.mock.support.Orders;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link AccountService} — the exact-arithmetic, double-entry funding math that
 * is the highest-risk code in the exchange. Constructs the service directly (it seeds ACC-1 / ACC-2
 * / ACC-EMPTY) so the money rules are asserted without routing through HTTP.
 */
class AccountServiceTest {

    private final AccountService accounts = new AccountService();

    @Test
    @DisplayName("a limit buy reserves price × quantity of the quote asset")
    void limitBuyReservesQuote() {
        Order buy = Orders.limit("ACC-1", "BTC-USD", Side.BUY, 30000, 1);

        accounts.reserve(buy);

        assertThat(available("ACC-1", "USD")).isEqualByComparingTo("970000");
        assertThat(reserved("ACC-1", "USD")).isEqualByComparingTo("30000");
        assertThat(buy.reservedAsset()).isEqualTo("USD");
        assertThat(buy.reservedAmount()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("a limit sell reserves quantity of the base asset")
    void limitSellReservesBase() {
        accounts.reserve(Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, 1));

        assertThat(available("ACC-1", "BTC")).isEqualByComparingTo("99");
        assertThat(reserved("ACC-1", "BTC")).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("a market order reserves nothing up-front")
    void marketOrderReservesNothing() {
        Order market = Orders.market("ACC-1", "BTC-USD", Side.BUY, 1);

        accounts.reserve(market);

        assertThat(available("ACC-1", "USD")).isEqualByComparingTo("1000000");
        assertThat(reserved("ACC-1", "USD")).isEqualByComparingTo("0");
        assertThat(market.reservedAsset()).isNull();
    }

    @Test
    @DisplayName("reserving more than available throws and leaves balances untouched")
    void insufficientFundsLeavesBalancesUntouched() {
        Order buy = Orders.limit("ACC-EMPTY", "BTC-USD", Side.BUY, 30000, 1);

        assertThatThrownBy(() -> accounts.reserve(buy))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(available("ACC-EMPTY", "USD")).isEqualByComparingTo("0");
        assertThat(reserved("ACC-EMPTY", "USD")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("releasing a reservation returns the funds to available exactly")
    void releaseReturnsReservedToAvailable() {
        Order buy = Orders.limit("ACC-1", "BTC-USD", Side.BUY, 30000, 1);
        accounts.reserve(buy);

        accounts.releaseRemaining(buy);

        assertThat(available("ACC-1", "USD")).isEqualByComparingTo("1000000");
        assertThat(reserved("ACC-1", "USD")).isEqualByComparingTo("0");
        assertThat(buy.reservedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("releasing an order that never reserved anything is a no-op")
    void releaseNeverReservedIsNoOp() {
        Order market = Orders.market("ACC-1", "BTC-USD", Side.BUY, 1); // reserve() is a no-op

        accounts.releaseRemaining(market);

        assertThat(available("ACC-1", "USD")).isEqualByComparingTo("1000000");
        assertThat(reserved("ACC-1", "USD")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("releasing an order whose reserve was fully consumed is a no-op")
    void releaseFullyConsumedIsNoOp() {
        Order sell = Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, 1);
        Order buy = Orders.limit("ACC-2", "BTC-USD", Side.BUY, 30000, 1);
        accounts.reserve(sell);
        accounts.reserve(buy);
        accounts.settleFill(buy, sell, new BigDecimal("30000"), BigDecimal.ONE);

        // The seller's reserve is now zero (asset still tagged); releasing must change nothing.
        BigDecimal btcBefore = available("ACC-1", "BTC");
        accounts.releaseRemaining(sell);

        assertThat(available("ACC-1", "BTC")).isEqualByComparingTo(btcBefore);
        assertThat(reserved("ACC-1", "BTC")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a fill settles both sides and refunds price improvement to the aggressor")
    void settleFillRefundsPriceImprovement() {
        Order sell = Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, 1);
        Order buy = Orders.limit("ACC-2", "BTC-USD", Side.BUY, 31000, 1);
        accounts.reserve(sell);
        accounts.reserve(buy);

        // Trade prints at the maker (sell) price of 30000.
        accounts.settleFill(buy, sell, new BigDecimal("30000"), BigDecimal.ONE);

        // Seller: -1 BTC, +30000 USD, nothing left reserved.
        assertThat(available("ACC-1", "BTC")).isEqualByComparingTo("99");
        assertThat(reserved("ACC-1", "BTC")).isEqualByComparingTo("0");
        assertThat(available("ACC-1", "USD")).isEqualByComparingTo("1030000");

        // Buyer: +1 BTC, pays only the maker price; 1000 of the 31000 reserved is refunded.
        assertThat(available("ACC-2", "BTC")).isEqualByComparingTo("101");
        assertThat(available("ACC-2", "USD")).isEqualByComparingTo("970000");
        assertThat(reserved("ACC-2", "USD")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a reservation conserves the asset total (available + reserved)")
    void reservationConservesTotal() {
        BigDecimal before = total("ACC-1", "USD");

        accounts.reserve(Orders.limit("ACC-1", "BTC-USD", Side.BUY, 30000, 1));

        assertThat(total("ACC-1", "USD")).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("a settled trade conserves each asset's total across both accounts")
    void settlementConservesSystemTotals() {
        BigDecimal usdBefore = systemTotal("USD");
        BigDecimal btcBefore = systemTotal("BTC");

        Order sell = Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, 1);
        Order buy = Orders.limit("ACC-2", "BTC-USD", Side.BUY, 31000, 1);
        accounts.reserve(sell);
        accounts.reserve(buy);
        accounts.settleFill(buy, sell, new BigDecimal("30000"), BigDecimal.ONE);

        // No money is created or destroyed: only ownership moves between the two accounts.
        assertThat(systemTotal("USD")).isEqualByComparingTo(usdBefore);
        assertThat(systemTotal("BTC")).isEqualByComparingTo(btcBefore);
    }

    private BigDecimal available(String accountId, String asset) {
        return accounts.account(accountId).balance(asset).available();
    }

    private BigDecimal reserved(String accountId, String asset) {
        return accounts.account(accountId).balance(asset).reserved();
    }

    private BigDecimal total(String accountId, String asset) {
        return accounts.account(accountId).balance(asset).total();
    }

    private BigDecimal systemTotal(String asset) {
        return total("ACC-1", asset).add(total("ACC-2", asset));
    }
}
