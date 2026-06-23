package com.exchange.mock.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the raw WebSocket endpoints for market data and order events. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MarketDataWebSocketHandler marketData;
    private final OrderEventsWebSocketHandler orderEvents;

    public WebSocketConfig(MarketDataWebSocketHandler marketData, OrderEventsWebSocketHandler orderEvents) {
        this.marketData = marketData;
        this.orderEvents = orderEvents;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketData, "/ws/marketdata").setAllowedOrigins("*");
        registry.addHandler(orderEvents, "/ws/orders").setAllowedOrigins("*");
    }
}
