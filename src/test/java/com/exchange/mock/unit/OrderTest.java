package com.exchange.mock.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.OrderStatus;
import com.exchange.mock.domain.Side;
import com.exchange.mock.support.Orders;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the {@link Order} aggregate's state transitions and reserve arithmetic. */
class OrderTest {

    @Test
    @DisplayName("recording fills advances NEW → PARTIALLY_FILLED → FILLED")
    void recordFillTransitionsStatus() {
        Order order = Orders.limit("ACC-1", "BTC-USD", Side.BUY, 30000, 1);
        assertThat(order.status()).isEqualTo(OrderStatus.NEW);

        order.recordFill(new BigDecimal("0.4"), Instant.now());
        assertThat(order.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.remainingQuantity()).isEqualByComparingTo("0.6");
        assertThat(order.isFullyFilled()).isFalse();

        order.recordFill(new BigDecimal("0.6"), Instant.now());
        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.isFullyFilled()).isTrue();
    }

    @Test
    @DisplayName("reserve sets the asset/amount and consumeReserved draws it down")
    void reserveAndConsume() {
        Order order = Orders.limit("ACC-1", "BTC-USD", Side.BUY, 30000, 1);

        order.reserve("USD", new BigDecimal("30000"));
        assertThat(order.reservedAsset()).isEqualTo("USD");
        assertThat(order.reservedAmount()).isEqualByComparingTo("30000");

        order.consumeReserved(new BigDecimal("12000"));
        assertThat(order.reservedAmount()).isEqualByComparingTo("18000");
    }

    @Test
    @DisplayName("markCancelled and markRejected set terminal status and reason")
    void terminalTransitions() {
        Order cancelled = Orders.limit("ACC-1", "BTC-USD", Side.BUY, 30000, 1);
        cancelled.markCancelled(Instant.now());
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);

        Order rejected = Orders.limit("ACC-1", "BTC-USD", Side.BUY, 30000, 1);
        rejected.markRejected("insufficient funds", Instant.now());
        assertThat(rejected.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejected.rejectReason()).isEqualTo("insufficient funds");
    }
}
