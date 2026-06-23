package com.exchange.mock.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.exchange.mock.engine.MatchingEngine;
import com.exchange.mock.event.BookChangedEvent;
import com.exchange.mock.service.ExchangeService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * Streams order book snapshots. A client connects to {@code /ws/marketdata?symbol=BTC-USD},
 * immediately receives a snapshot, and then a fresh snapshot whenever that book changes.
 */
@Component
public class MarketDataWebSocketHandler extends JsonWebSocketHandler {

    private final ExchangeService exchange;
    private final Map<String, Set<WebSocketSession>> sessionsBySymbol = new ConcurrentHashMap<>();

    public MarketDataWebSocketHandler(ExchangeService exchange, ObjectMapper objectMapper) {
        super(objectMapper);
        this.exchange = exchange;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String symbol = queryParams(session).get("symbol");
        if (symbol == null || !exchange.supportedSymbols().contains(symbol)) {
            sendJson(session, Map.of("type", "ERROR", "message", "unknown or missing symbol"));
            closeQuietly(session);
            return;
        }
        sessionsBySymbol.computeIfAbsent(symbol, s -> new CopyOnWriteArraySet<>()).add(session);
        session.getAttributes().put("symbol", symbol);
        // Initial snapshot so a new subscriber has immediate state.
        sendJson(session, exchange.orderBook(symbol, MatchingEngine.DEFAULT_DEPTH));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object symbol = session.getAttributes().get("symbol");
        if (symbol != null) {
            Set<WebSocketSession> sessions = sessionsBySymbol.get(symbol.toString());
            if (sessions != null) {
                sessions.remove(session);
            }
        }
    }

    @EventListener
    public void onBookChanged(BookChangedEvent event) {
        Set<WebSocketSession> sessions = sessionsBySymbol.get(event.symbol());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        var snapshot = exchange.orderBook(event.symbol(), MatchingEngine.DEFAULT_DEPTH);
        sessions.forEach(session -> sendJson(session, snapshot));
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.BAD_DATA);
        } catch (Exception ignored) {
            // best effort
        }
    }
}
