package com.exchange.mock.api;

import com.exchange.mock.domain.OrderType;
import com.exchange.mock.domain.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request body for placing an order. Structural constraints (presence, positivity) are enforced by
 * bean validation; the conditional rule "limit orders require a price" is enforced in the service
 * layer so both REST and FIX share it.
 */
public record PlaceOrderRequest(
        @NotBlank String accountId,
        String clientOrderId,
        @NotBlank String symbol,
        @NotNull Side side,
        @NotNull OrderType type,
        @Positive BigDecimal price,
        @NotNull @Positive BigDecimal quantity) {
}
