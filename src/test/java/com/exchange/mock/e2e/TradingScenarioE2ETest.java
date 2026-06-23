package com.exchange.mock.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.exchange.mock.domain.Side;
import com.exchange.mock.support.FixTestClient;
import com.exchange.mock.support.IntegrationTestBase;
import com.exchange.mock.support.OrderRequests;
import com.exchange.mock.support.WsTestClient;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quickfix.Message;
import quickfix.field.ExecType;
import quickfix.field.OrdStatus;

/**
 * The headline scenario: an order resting on the book via <b>FIX</b> is filled by an aggressive
 * <b>REST</b> order, and the fill is observed consistently across all three protocols —
 * <b>FIX</b> ExecutionReport, <b>WebSocket</b> market data + order events, and <b>REST</b> state and
 * balances. This is the strongest evidence that the single shared engine keeps every transport in
 * sync.
 */
class TradingScenarioE2ETest extends IntegrationTestBase {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private FixTestClient fix;

    @BeforeEach
    void connectFix() {
        fix = new FixTestClient(FIX_PORT).start();
    }

    @AfterEach
    void disconnectFix() {
        if (fix != null) {
            fix.close();
        }
    }

    @Test
    @DisplayName("a FIX resting sell filled by a REST buy is consistent across FIX, WebSocket and REST")
    void crossProtocolFillIsConsistent() {
        try (WsTestClient marketData = WsTestClient.connect(httpPort, "/ws/marketdata?symbol=BTC-USD");
             WsTestClient orderEvents = WsTestClient.connect(httpPort, "/ws/orders?accountId=ACC-1")) {

            marketData.awaitMessage(TIMEOUT); // initial empty snapshot

            // 1) Rest a SELL on the book via FIX (account ACC-1).
            fix.newLimitOrder("S1", "ACC-1", "BTC-USD", Side.SELL, new BigDecimal("1"), new BigDecimal("30000"));
            fix.awaitMessage(m -> FixTestClient.isExecutionReport(m)
                    && FixTestClient.execType(m) == ExecType.NEW
                    && FixTestClient.clOrdId(m).equals("S1"), TIMEOUT);
            marketData.awaitMessage(jp -> !jp.getList("asks").isEmpty(), TIMEOUT);
            orderEvents.awaitMessage(typeIs("ACCEPTED"), TIMEOUT);

            // 2) Aggress the book with a BUY via REST (account ACC-2).
            String buyId = placeOrderId(OrderRequests.limit("ACC-2", "BTC-USD", "BUY", 30000, 1));

            // 3a) FIX: the resting sell receives an asynchronous fill ExecutionReport.
            Message fixFill = fix.awaitMessage(m -> FixTestClient.isExecutionReport(m)
                    && FixTestClient.execType(m) == ExecType.TRADE
                    && FixTestClient.ordStatus(m) == OrdStatus.FILLED, TIMEOUT);
            assertThat(FixTestClient.clOrdId(fixFill)).isEqualTo("S1");

            // 3b) WebSocket market data: the book is now empty.
            marketData.awaitMessage(jp -> jp.getList("asks").isEmpty() && jp.getList("bids").isEmpty(), TIMEOUT);

            // 3c) WebSocket order events: ACC-1 sees its sell FILLED.
            JsonPath filledEvent = orderEvents.awaitMessage(typeIs("FILLED"), TIMEOUT);
            assertThat(filledEvent.getString("order.accountId")).isEqualTo("ACC-1");

            // 3d) REST: the aggressing order is FILLED and balances settled on both accounts.
            getOrder(buyId).then().body("status", equalTo("FILLED"));

            Response seller = account("ACC-1");
            assertThat(balance(seller, "BTC", "available")).isEqualByComparingTo("99");
            assertThat(balance(seller, "USD", "available")).isEqualByComparingTo("1030000");

            Response buyer = account("ACC-2");
            assertThat(balance(buyer, "BTC", "available")).isEqualByComparingTo("101");
            assertThat(balance(buyer, "USD", "available")).isEqualByComparingTo("970000");
        }
    }

    private static java.util.function.Predicate<JsonPath> typeIs(String type) {
        return jp -> type.equals(jp.getString("type"));
    }

    private static BigDecimal balance(Response account, String asset, String field) {
        return new BigDecimal(account.jsonPath().getString("balances." + asset + "." + field));
    }
}
