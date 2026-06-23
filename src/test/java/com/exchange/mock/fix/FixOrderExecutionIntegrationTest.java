package com.exchange.mock.fix;

import static org.assertj.core.api.Assertions.assertThat;

import com.exchange.mock.domain.Side;
import com.exchange.mock.support.FixTestClient;
import com.exchange.mock.support.IntegrationTestBase;
import com.exchange.mock.support.OrderRequests;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quickfix.Message;
import quickfix.field.CumQty;
import quickfix.field.ExecType;
import quickfix.field.LastPx;
import quickfix.field.LastQty;
import quickfix.field.OrdStatus;
import quickfix.field.Text;

/**
 * FIX 4.4 order execution via QuickFIX/J: NewOrderSingle is acknowledged and executed with
 * ExecutionReports, invalid orders are rejected, and OrderCancelRequest is honoured. The FIX client
 * logs on to the in-process acceptor on a per-test isolated port.
 */
class FixOrderExecutionIntegrationTest extends IntegrationTestBase {

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
    @DisplayName("NewOrderSingle is acknowledged with an ExecutionReport (ExecType=NEW)")
    void newOrderAcknowledged() {
        fix.newLimitOrder("C1", "ACC-1", "BTC-USD", Side.BUY, new BigDecimal("1"), new BigDecimal("30000"));

        Message report = fix.awaitMessage(
                m -> FixTestClient.isExecutionReport(m) && FixTestClient.execType(m) == ExecType.NEW, TIMEOUT);

        assertThat(FixTestClient.clOrdId(report)).isEqualTo("C1");
        assertThat(FixTestClient.ordStatus(report)).isEqualTo(OrdStatus.NEW);
    }

    @Test
    @DisplayName("a FIX order fills against a resting REST order and reports a TRADE / FILLED")
    void fixOrderFillsAgainstRestingOrder() {
        placeOrderId(OrderRequests.limit("ACC-1", "BTC-USD", "SELL", 30000, 1)); // resting liquidity (REST)

        fix.newLimitOrder("C2", "ACC-2", "BTC-USD", Side.BUY, new BigDecimal("1"), new BigDecimal("30000"));

        Message fill = fix.awaitMessage(m -> FixTestClient.isExecutionReport(m)
                && FixTestClient.execType(m) == ExecType.TRADE
                && FixTestClient.ordStatus(m) == OrdStatus.FILLED, TIMEOUT);

        assertThat(FixTestClient.clOrdId(fill)).isEqualTo("C2");
        assertThat(FixTestClient.field(fill, LastQty.FIELD)).isEqualTo(1.0);
        assertThat(FixTestClient.field(fill, LastPx.FIELD)).isEqualTo(30000.0);
        assertThat(FixTestClient.field(fill, CumQty.FIELD)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("an order for an unlisted symbol is rejected with an ExecutionReport (ExecType=REJECTED)")
    void invalidOrderRejected() {
        fix.newLimitOrder("C3", "ACC-1", "FOO-BAR", Side.BUY, new BigDecimal("1"), new BigDecimal("1"));

        Message reject = fix.awaitMessage(
                m -> FixTestClient.isExecutionReport(m) && FixTestClient.execType(m) == ExecType.REJECTED, TIMEOUT);

        assertThat(FixTestClient.clOrdId(reject)).isEqualTo("C3");
        assertThat(FixTestClient.string(reject, Text.FIELD)).contains("not listed");
    }

    @Test
    @DisplayName("OrderCancelRequest cancels a resting FIX order and reports CANCELED")
    void cancelRequestCancelsOrder() {
        fix.newLimitOrder("C4", "ACC-1", "BTC-USD", Side.BUY, new BigDecimal("1"), new BigDecimal("30000"));
        fix.awaitMessage(m -> FixTestClient.isExecutionReport(m) && FixTestClient.execType(m) == ExecType.NEW, TIMEOUT);

        fix.cancel("X4", "C4", "BTC-USD", Side.BUY);

        Message cancelled = fix.awaitMessage(
                m -> FixTestClient.isExecutionReport(m) && FixTestClient.execType(m) == ExecType.CANCELED, TIMEOUT);
        assertThat(FixTestClient.clOrdId(cancelled)).isEqualTo("C4");
        assertThat(FixTestClient.ordStatus(cancelled)).isEqualTo(OrdStatus.CANCELED);
    }

    @Test
    @DisplayName("cancelling an unknown order yields an OrderCancelReject")
    void cancelUnknownOrderRejected() {
        fix.cancel("X5", "UNKNOWN-ORIG", "BTC-USD", Side.BUY);

        Message reject = fix.awaitMessage(FixTestClient::isOrderCancelReject, TIMEOUT);
        assertThat(reject).isNotNull();
    }
}
