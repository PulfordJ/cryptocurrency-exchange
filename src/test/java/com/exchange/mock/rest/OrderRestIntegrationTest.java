package com.exchange.mock.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.exchange.mock.support.IntegrationTestBase;
import com.exchange.mock.support.OrderRequests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Happy-path order lifecycle over REST: place, query, list, cancel, and their status codes. */
class OrderRestIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("placing a limit order returns 201 with NEW status and a Location header")
    void placeLimitOrder() {
        placeOrder(OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 0.5))
                .then()
                .statusCode(201)
                .header("Location", containsString("/api/orders/"))
                .body("orderId", notNullValue())
                .body("status", equalTo("NEW"))
                .body("filledQuantity", equalTo(0))
                .body("remainingQuantity", equalTo(0.5f));
    }

    @Test
    @DisplayName("a placed order can be fetched by id")
    void getPlacedOrder() {
        String id = placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 0.5));

        getOrder(id).then()
                .statusCode(200)
                .body("orderId", equalTo(id))
                .body("symbol", equalTo("BTC-USD"))
                .body("side", equalTo("BUY"));
    }

    @Test
    @DisplayName("fetching an unknown order returns 404")
    void getUnknownOrder() {
        getOrder("ORD-does-not-exist").then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("listing orders can be filtered by account")
    void listOrdersByAccount() {
        placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 0.5));
        placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 29000, 0.5));

        given().get("/api/orders?accountId=ACC-2").then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].accountId", equalTo("ACC-2"));
    }

    @Test
    @DisplayName("cancelling a resting order returns CANCELLED and removes it from the book")
    void cancelRestingOrder() {
        String id = placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "BUY", 30000, 1));

        cancelOrder(id).then().statusCode(200).body("status", equalTo("CANCELLED"));
        orderBook("BTC-USD").then().statusCode(200).body("bids.size()", equalTo(0));
    }

    @Test
    @DisplayName("cancelling an unknown order returns 404")
    void cancelUnknownOrder() {
        cancelOrder("ORD-does-not-exist").then().statusCode(404);
    }

    @Test
    @DisplayName("cancelling an already-filled order returns 409 Conflict")
    void cancelFilledOrder() {
        String sellId = placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 1));
        placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 30000, 1)); // fills the sell

        getOrder(sellId).then().body("status", equalTo("FILLED"));
        cancelOrder(sellId).then()
                .statusCode(409)
                .body("message", containsString("cannot be cancelled"));
    }
}
