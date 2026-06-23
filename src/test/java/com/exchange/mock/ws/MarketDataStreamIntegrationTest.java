package com.exchange.mock.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.exchange.mock.support.IntegrationTestBase;
import com.exchange.mock.support.OrderRequests;
import com.exchange.mock.support.WsTestClient;
import io.restassured.path.json.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Market-data streaming: snapshot on connect, then incremental book updates as orders arrive. */
class MarketDataStreamIntegrationTest extends IntegrationTestBase {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    @DisplayName("a new subscriber immediately receives an order book snapshot")
    void snapshotOnConnect() {
        try (WsTestClient client = WsTestClient.connect(httpPort, "/ws/marketdata?symbol=BTC-USD")) {
            JsonPath snapshot = JsonPath.from(client.awaitMessage(TIMEOUT));

            assertThat(snapshot.getString("symbol")).isEqualTo("BTC-USD");
            assertThat(snapshot.getList("bids")).isEmpty();
            assertThat(snapshot.getList("asks")).isEmpty();
        }
    }

    @Test
    @DisplayName("placing an order pushes an updated book to subscribers")
    void bookUpdateAfterOrder() {
        try (WsTestClient client = WsTestClient.connect(httpPort, "/ws/marketdata?symbol=BTC-USD")) {
            client.awaitMessage(TIMEOUT); // initial (empty) snapshot

            placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 1));

            JsonPath update = client.awaitMessage(jp -> !jp.getList("asks").isEmpty(), TIMEOUT);
            assertThat(update.getDouble("asks[0].price")).isEqualTo(30000.0);
            assertThat(update.getDouble("asks[0].quantity")).isEqualTo(1.0);
            assertThat(update.getInt("asks[0].orderCount")).isEqualTo(1);
        }
    }
}
