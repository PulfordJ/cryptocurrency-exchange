package com.exchange.mock.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.exchange.mock.support.IntegrationTestBase;
import io.restassured.response.Response;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The account query endpoint: {@code GET /api/accounts/{id}} returns a balance snapshot, and an
 * unknown account is a 404.
 */
class AccountQueryIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("a known account returns its seeded balances")
    void knownAccountReturnsBalances() {
        Response account = account("ACC-1");
        account.then().statusCode(200).body("accountId", equalTo("ACC-1"));

        // Read amounts as strings, like AccountFundingIntegrationTest, to stay independent of how
        // RestAssured coerces JSON number types.
        assertThat(amount(account, "USD", "available")).isEqualByComparingTo("1000000");
        assertThat(amount(account, "USD", "reserved")).isEqualByComparingTo("0");
        assertThat(amount(account, "BTC", "available")).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("an unknown account is a 404")
    void unknownAccountIsNotFound() {
        account("ACC-DOES-NOT-EXIST").then().statusCode(404);
    }

    private static BigDecimal amount(Response account, String asset, String field) {
        return new BigDecimal(account.jsonPath().getString("balances." + asset + "." + field));
    }
}
