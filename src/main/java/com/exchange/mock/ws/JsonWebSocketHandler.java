package com.exchange.mock.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Base class for handlers that push JSON, with helpers for query parsing and safe sends. */
abstract class JsonWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(JsonWebSocketHandler.class);

    protected final ObjectMapper objectMapper;

    protected JsonWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Serialize {@code payload} and send it; never throws (a dead client must not break others). */
    protected void sendJson(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            // WebSocketSession is not safe for concurrent sends; serialize per session.
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException | RuntimeException e) {
            logger.debug("Failed to send to session {}: {}", session.getId(), e.getMessage());
        }
    }

    /** Parse the connection query string into a key→value map (e.g. {@code symbol=BTC-USD}). */
    protected static Map<String, String> queryParams(WebSocketSession session) {
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        return java.util.Arrays.stream(query.split("&"))
                .map(p -> p.split("=", 2))
                .filter(kv -> kv.length == 2)
                .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1], (a, b) -> b));
    }
}
