package com.exchange.mock.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import com.exchange.mock.support.IntegrationTestBase;
import com.exchange.mock.support.OrderRequests;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Risk-focused negative testing: malformed and invalid requests must be rejected with the right
 * status code <em>before</em> any order is created. Validation failures (4xx) are distinct from
 * business rejections (a created order with REJECTED status) — see {@link AccountFundingIntegrationTest}.
 */
class OrderValidationIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("negative quantity is rejected with 400")
    void negativeQuantity() {
        placeOrder(OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, -1)).then().statusCode(400);
    }

    @Test
    @DisplayName("zero quantity is rejected with 400")
    void zeroQuantity() {
        placeOrder(OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 0)).then().statusCode(400);
    }

    @Test
    @DisplayName("a limit order without a price is rejected with 400")
    void limitWithoutPrice() {
        java.util.Map<String, Object> body = OrderRequests.market("ACC-1", "BTC-USD", "BUY", 1);
        body.put("type", "LIMIT"); // LIMIT but no price field
        placeOrder(body).then()
                .statusCode(400)
                .body("message", containsString("limit orders require a price"));
    }

    @Test
    @DisplayName("an unlisted symbol is rejected with 422")
    void unknownSymbol() {
        placeOrder(OrderRequests.limit("ACC-1", "DOGE-USD", "BUY", 1, 1)).then()
                .statusCode(422)
                .body("message", containsString("not listed"));
    }

    @Test
    @DisplayName("an unknown account is rejected with 404")
    void unknownAccount() {
        placeOrder(OrderRequests.limit("ACC-NOPE", "BTC-USD", "BUY", 30000, 1)).then().statusCode(404);
    }

    @Test
    @DisplayName("malformed JSON is rejected with 400")
    void malformedJson() {
        given().contentType(ContentType.JSON).body("{ not valid json ")
                .post("/api/orders").then().statusCode(400);
    }

    @Test
    @DisplayName("an invalid enum value is rejected with 400")
    void invalidEnum() {
        given().contentType(ContentType.JSON)
                .body(OrderRequests.limit("ACC-1", "BTC-USD", "HODL", 30000, 1))
                .post("/api/orders").then().statusCode(400);
    }

    @Test
    @DisplayName("a blank clientOrderId is treated as absent and bypasses the idempotency guard")
    void blankClientOrderIdIsNotAnIdempotencyKey() {
        placeOrder(OrderRequests.withClientOrderId(
                OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 1), ""))
                .then().statusCode(201);

        // A second blank key must not be rejected as a duplicate.
        placeOrder(OrderRequests.withClientOrderId(
                OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 1), ""))
                .then().statusCode(201);
    }

    @Test
    @DisplayName("reusing a clientOrderId for an account returns 409 (idempotency guard)")
    void duplicateClientOrderId() {
        placeOrder(OrderRequests.withClientOrderId(
                OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 1), "dup-1"))
                .then().statusCode(201);

        placeOrder(OrderRequests.withClientOrderId(
                OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 1), "dup-1"))
                .then()
                .statusCode(409)
                .body("message", equalTo("clientOrderId already used for account ACC-1: dup-1"));
    }
}
