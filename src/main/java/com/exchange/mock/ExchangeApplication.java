package com.exchange.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mock cryptocurrency exchange.
 *
 * <p>Exposes the same matching engine over three protocols:
 * <ul>
 *   <li><b>REST</b> ({@code /api/...}) — order management and queries</li>
 *   <li><b>WebSocket</b> ({@code /ws/marketdata}, {@code /ws/orders}) — streaming market data and
 *       asynchronous order events</li>
 *   <li><b>FIX 4.4</b> (acceptor, default port 9876) — order execution via QuickFIX/J</li>
 * </ul>
 */
@SpringBootApplication
public class ExchangeApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExchangeApplication.class, args);
    }
}
