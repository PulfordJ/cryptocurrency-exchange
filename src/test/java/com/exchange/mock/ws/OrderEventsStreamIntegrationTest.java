package com.exchange.mock.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exchange.mock.support.IntegrationTestBase;
import com.exchange.mock.support.OrderRequests;
import com.exchange.mock.support.WsTestClient;
import io.restassured.path.json.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Asynchronous order-event streaming: lifecycle transitions and per-account filtering. */
class OrderEventsStreamIntegrationTest extends IntegrationTestBase {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    @DisplayName("an account's order stream reports ACCEPTED → PARTIALLY_FILLED → FILLED in order")
    void lifecycleEventsInOrder() {
        // Resting liquidity (ACC-1 events are filtered out for an ACC-2 subscriber).
        placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 0.4));

        try (WsTestClient client = WsTestClient.connect(httpPort, "/ws/orders?accountId=ACC-2")) {
            // Buy 1 BTC: 0.4 fills immediately (partial), 0.6 rests.
            placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 30000, 1));

            JsonPath accepted = client.awaitMessage(typeIs("ACCEPTED"), TIMEOUT);
            assertThat(accepted.getString("order.accountId")).isEqualTo("ACC-2");

            JsonPath partial = client.awaitMessage(typeIs("PARTIALLY_FILLED"), TIMEOUT);
            assertThat(partial.getDouble("order.filledQuantity")).isEqualTo(0.4);

            // Provide the remaining liquidity; the resting buy now fully fills.
            placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 0.6));

            JsonPath filled = client.awaitMessage(typeIs("FILLED"), TIMEOUT);
            assertThat(filled.getDouble("order.remainingQuantity")).isEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("subscribers only receive events for their own account")
    void eventsFilteredByAccount() {
        try (WsTestClient client = WsTestClient.connect(httpPort, "/ws/orders?accountId=ACC-1")) {
            // Activity only on ACC-2; the ACC-1 subscriber must see nothing.
            placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 30000, 1));

            assertThatThrownBy(() -> client.awaitMessage(Duration.ofMillis(800)))
                    .isInstanceOf(AssertionError.class);
        }
    }

    private static java.util.function.Predicate<JsonPath> typeIs(String type) {
        return jp -> type.equals(jp.getString("type"));
    }
}
