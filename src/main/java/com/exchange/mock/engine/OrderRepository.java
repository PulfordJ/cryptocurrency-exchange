package com.exchange.mock.engine;

import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.Trade;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import org.springframework.stereotype.Component;

/** In-memory store of all orders and executed trades. */
@Component
public class OrderRepository {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Trade> trades = new ConcurrentLinkedQueue<>();

    public void save(Order order) {
        orders.put(order.id(), order);
    }

    public Optional<Order> find(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public List<Order> all() {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::createdAt))
                .toList();
    }

    public void saveTrade(Trade trade) {
        trades.add(trade);
    }

    public List<Trade> trades() {
        return new ArrayList<>(trades);
    }

    public void reset() {
        orders.clear();
        trades.clear();
    }
}
