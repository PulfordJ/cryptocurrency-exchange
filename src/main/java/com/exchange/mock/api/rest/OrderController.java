package com.exchange.mock.api.rest;

import com.exchange.mock.api.OrderView;
import com.exchange.mock.api.PlaceOrderRequest;
import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.OrderSource;
import com.exchange.mock.service.ExchangeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order management over REST.
 *
 * <p>Design note: structural/validation problems (bad JSON, missing fields, unlisted symbol,
 * unknown account, duplicate clientOrderId) are rejected with a 4xx and <em>no</em> order is
 * created. A request that is well-formed always creates an order resource (201); business outcomes
 * such as insufficient funds or no liquidity are reported as a {@code REJECTED} status on that
 * resource, mirroring how a real venue acknowledges then rejects.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final ExchangeService exchange;

    public OrderController(ExchangeService exchange) {
        this.exchange = exchange;
    }

    @PostMapping
    public ResponseEntity<OrderView> place(@Valid @RequestBody PlaceOrderRequest request) {
        Order order = exchange.placeOrder(request, OrderSource.REST);
        return ResponseEntity
                .created(URI.create("/api/orders/" + order.id()))
                .body(OrderView.of(order));
    }

    @GetMapping("/{orderId}")
    public OrderView get(@PathVariable String orderId) {
        return OrderView.of(exchange.getOrder(orderId));
    }

    @GetMapping
    public List<OrderView> list(@RequestParam(required = false) String accountId) {
        return exchange.listOrders(accountId).stream().map(OrderView::of).toList();
    }

    @DeleteMapping("/{orderId}")
    public OrderView cancel(@PathVariable String orderId) {
        return OrderView.of(exchange.cancelOrder(orderId));
    }
}
