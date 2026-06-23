package com.exchange.mock.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.exchange.mock.api.OrderBookView;
import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.Side;
import com.exchange.mock.domain.Symbol;
import com.exchange.mock.engine.OrderBook;
import com.exchange.mock.support.Orders;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link OrderBook} — the price-ordered, FIFO-within-a-level data structure.
 * No Spring context, no funding: this isolates aggregation, ordering and removal semantics.
 */
class OrderBookTest {

    private final OrderBook book = new OrderBook(Symbol.parse("BTC-USD"));

    @Test
    @DisplayName("a snapshot aggregates quantity and order count per price level")
    void snapshotAggregatesLevel() {
        book.rest(Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, 1));
        book.rest(Orders.limit("ACC-2", "BTC-USD", Side.SELL, 30000, "0.5"));

        OrderBookView.PriceLevel ask = book.snapshot(10).asks().get(0);
        assertThat(ask.price()).isEqualByComparingTo("30000");
        assertThat(ask.quantity()).isEqualByComparingTo("1.5");
        assertThat(ask.orderCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("bids are ordered high→low and asks low→high")
    void priceOrdering() {
        book.rest(Orders.limit("ACC-1", "BTC-USD", Side.BUY, 30000, 1));
        book.rest(Orders.limit("ACC-1", "BTC-USD", Side.BUY, 31000, 1));
        book.rest(Orders.limit("ACC-1", "BTC-USD", Side.SELL, 33000, 1));
        book.rest(Orders.limit("ACC-1", "BTC-USD", Side.SELL, 32000, 1));

        OrderBookView snapshot = book.snapshot(10);
        assertThat(snapshot.bids()).extracting(l -> l.price().intValueExact())
                .containsExactly(31000, 30000);
        assertThat(snapshot.asks()).extracting(l -> l.price().intValueExact())
                .containsExactly(32000, 33000);
    }

    @Test
    @DisplayName("a snapshot returns at most `depth` levels per side")
    void depthLimiting() {
        for (int price = 30000; price < 30005; price++) {
            book.rest(Orders.limit("ACC-1", "BTC-USD", Side.SELL, price, 1));
        }

        assertThat(book.snapshot(2).asks()).hasSize(2);
    }

    @Test
    @DisplayName("removing the last order at a price drops the level entirely")
    void removeEmptiesLevel() {
        Order order = Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, 1);
        book.rest(order);

        book.remove(order);

        assertThat(book.snapshot(10).asks()).isEmpty();
    }

    @Test
    @DisplayName("removing a market order (null price) is a no-op")
    void removeMarketOrderIsNoOp() {
        book.rest(Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, 1));

        book.remove(Orders.market("ACC-2", "BTC-USD", Side.SELL, 1)); // null price

        assertThat(book.snapshot(10).asks()).hasSize(1);
    }

    @Test
    @DisplayName("oppositeSide maps a buy to the asks and a sell to the bids")
    void oppositeSideMapping() {
        Order ask = Orders.limit("ACC-1", "BTC-USD", Side.SELL, 30000, 1);
        Order bid = Orders.limit("ACC-1", "BTC-USD", Side.BUY, 29000, 1);
        book.rest(ask);
        book.rest(bid);

        assertThat(book.oppositeSide(Side.BUY).firstKey()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(book.oppositeSide(Side.SELL).firstKey()).isEqualByComparingTo(new BigDecimal("29000"));
    }
}
