package com.exchange.mock.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import com.exchange.mock.support.IntegrationTestBase;
import com.exchange.mock.support.OrderRequests;
import io.restassured.response.Response;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Funding behaviour — the heart of the "Funding" squad: orders reserve funds, cancellations release
 * them, and fills settle both sides with exact double-entry bookkeeping (including price improvement
 * refunds when a trade prints better than an aggressive limit price).
 */
class AccountFundingIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("a limit buy reserves the quote asset (USD) and reduces available")
    void limitBuyReservesQuote() {
        placeOrder(OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 1)).then().statusCode(201);

        Response account = account("ACC-1");
        assertThat(available(account, "USD")).isEqualByComparingTo("970000");
        assertThat(reserved(account, "USD")).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("an order with insufficient funds is REJECTED and leaves balances untouched")
    void insufficientFundsRejectsAndPreservesBalances() {
        placeOrder(OrderRequests.limit("ACC-EMPTY", "BTC-USD", "BUY", 30000, 1)).then()
                .statusCode(201)
                .body("status", equalTo("REJECTED"))
                .body("rejectReason", containsString("available"));

        Response account = account("ACC-EMPTY");
        assertThat(available(account, "USD")).isEqualByComparingTo("0");
        assertThat(reserved(account, "USD")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("cancelling an order releases its reserved funds back to available")
    void cancelReleasesReservedFunds() {
        String id = placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 1));
        cancelOrder(id).then().statusCode(200);

        Response account = account("ACC-1");
        assertThat(available(account, "USD")).isEqualByComparingTo("1000000");
        assertThat(reserved(account, "USD")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("cancelling a partially-filled order releases only the unfilled remainder")
    void cancelAfterPartialFillReleasesRemainder() {
        // ACC-1 rests a sell of 1 BTC; ACC-2 takes 0.4 of it, leaving 0.6 working.
        String sellId = placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 1));
        placeOrder(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 30000, 0.4)).then()
                .statusCode(201)
                .body("status", equalTo("FILLED"));

        cancelOrder(sellId).then().statusCode(200);

        // Seller keeps proceeds of the 0.4 sold; the unsold 0.6 BTC returns to available.
        Response seller = account("ACC-1");
        assertThat(available(seller, "BTC")).isEqualByComparingTo("99.6");
        assertThat(reserved(seller, "BTC")).isEqualByComparingTo("0");
        assertThat(available(seller, "USD")).isEqualByComparingTo("1012000");
    }

    @Test
    @DisplayName("a fill settles both accounts, refunding price improvement to the aggressor")
    void fillSettlesBothAccountsWithPriceImprovement() {
        // Maker rests a sell at 30000; aggressor buys with a higher limit of 31000.
        placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 1));
        placeOrder(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 31000, 1)).then()
                .statusCode(201)
                .body("status", equalTo("FILLED"));

        // Seller (ACC-1): -1 BTC, +30000 USD; no funds left reserved.
        Response seller = account("ACC-1");
        assertThat(available(seller, "BTC")).isEqualByComparingTo("99");
        assertThat(reserved(seller, "BTC")).isEqualByComparingTo("0");
        assertThat(available(seller, "USD")).isEqualByComparingTo("1030000");

        // Buyer (ACC-2): +1 BTC, pays only 30000 (maker price), refunded 1000 of the 31000 reserved.
        Response buyer = account("ACC-2");
        assertThat(available(buyer, "BTC")).isEqualByComparingTo("101");
        assertThat(available(buyer, "USD")).isEqualByComparingTo("970000");
        assertThat(reserved(buyer, "USD")).isEqualByComparingTo("0");
    }

    private static BigDecimal available(Response account, String asset) {
        return new BigDecimal(account.jsonPath().getString("balances." + asset + ".available"));
    }

    private static BigDecimal reserved(Response account, String asset) {
        return new BigDecimal(account.jsonPath().getString("balances." + asset + ".reserved"));
    }
}
