package com.exchange.mock.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.exchange.mock.event.OrderEvent;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * Streams asynchronous order-lifecycle events (ACCEPTED → PARTIALLY_FILLED → FILLED / CANCELLED /
 * REJECTED). A client connects to {@code /ws/orders?accountId=ACC-1} to receive only its account's
 * events, or {@code /ws/orders} to receive all of them.
 */
@Component
public class OrderEventsWebSocketHandler extends JsonWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public OrderEventsWebSocketHandler(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String accountId = queryParams(session).get("accountId");
        if (accountId != null) {
            session.getAttributes().put("accountId", accountId);
        }
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @EventListener
    public void onOrderEvent(OrderEvent event) {
        String account = event.order().accountId();
        for (WebSocketSession session : sessions) {
            Object filter = session.getAttributes().get("accountId");
            if (filter == null || filter.equals(account)) {
                sendJson(session, event);
            }
        }
    }
}
