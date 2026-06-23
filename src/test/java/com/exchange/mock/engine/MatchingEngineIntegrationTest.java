package com.exchange.mock.engine;

import static org.hamcrest.Matchers.equalTo;

import com.exchange.mock.support.IntegrationTestBase;
import com.exchange.mock.support.OrderRequests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Matching engine semantics exercised through REST: crossing, partial fills, price-time priority,
 * best-price selection, and market-order (immediate-or-cancel) behaviour.
 */
class MatchingEngineIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("a crossing limit order trades and both orders become FILLED")
    void crossingLimitProducesTrade() {
        String sellId = placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 1));
        String buyId = placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 30000, 1));

        getOrder(sellId).then().body("status", equalTo("FILLED"));
        getOrder(buyId).then().body("status", equalTo("FILLED"));
        orderBook("BTC-USD").then()
                .body("bids.size()", equalTo(0))
                .body("asks.size()", equalTo(0));
    }

    @Test
    @DisplayName("a resting order fills partially then fully across two incoming orders")
    void partialThenFullFill() {
        String sellId = placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 1));

        placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 30000, 0.4));
        getOrder(sellId).then()
                .body("status", equalTo("PARTIALLY_FILLED"))
                .body("filledQuantity", equalTo(0.4f))
                .body("remainingQuantity", equalTo(0.6f));

        placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 30000, 0.6));
        getOrder(sellId).then().body("status", equalTo("FILLED"));
    }

    @Test
    @DisplayName("at equal price, the earliest order fills first (price-time priority)")
    void priceTimePriority() {
        String first = placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 1));
        String second = placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "SELL", 30000, 1));

        placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 30000, 1)); // fills exactly one

        getOrder(first).then().body("status", equalTo("FILLED"));
        getOrder(second).then().body("status", equalTo("NEW"));
    }

    @Test
    @DisplayName("the best-priced resting order is taken first")
    void bestPriceFillsFirst() {
        String cheap = placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 1));
        String pricey = placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "SELL", 31000, 1));

        placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 31000, 1));

        getOrder(cheap).then().body("status", equalTo("FILLED"));
        getOrder(pricey).then().body("status", equalTo("NEW"));
    }

    @Test
    @DisplayName("a market order fills against the resting book")
    void marketOrderFillsAgainstBook() {
        placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 1));

        placeOrder(OrderRequests.market("ACC-2", "BTC-USD", "BUY", 0.5)).then()
                .statusCode(201)
                .body("status", equalTo("FILLED"))
                .body("filledQuantity", equalTo(0.5f));

        orderBook("BTC-USD").then().body("asks[0].quantity", equalTo(0.5f));
    }

    @Test
    @DisplayName("a market order with no liquidity is REJECTED")
    void marketOrderNoLiquidityRejected() {
        placeOrder(OrderRequests.market("ACC-2", "BTC-USD", "BUY", 1)).then()
                .statusCode(201)
                .body("status", equalTo("REJECTED"))
                .body("rejectReason", equalTo("no liquidity available for market order"));
    }
}
