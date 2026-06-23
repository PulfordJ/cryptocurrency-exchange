package com.exchange.mock.support;

import io.restassured.path.json.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Minimal WebSocket test client built on the JDK HTTP client (no extra dependencies). Collects
 * inbound text frames into a queue so tests can await and assert on streamed JSON messages.
 */
public class WsTestClient implements AutoCloseable {

    private final WebSocket webSocket;
    private final BlockingQueue<String> messages;

    private WsTestClient(WebSocket webSocket, BlockingQueue<String> messages) {
        this.webSocket = webSocket;
        this.messages = messages;
    }

    public static WsTestClient connect(int port, String pathAndQuery) {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) {
                    messages.add(buffer.toString());
                    buffer.setLength(0);
                }
                webSocket.request(1);
                return null;
            }
        };
        WebSocket socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + pathAndQuery), listener)
                .join();
        return new WsTestClient(socket, messages);
    }

    /** Await the next raw message, failing if none arrives within the timeout. */
    public String awaitMessage(Duration timeout) {
        try {
            String message = messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (message == null) {
                throw new AssertionError("no WebSocket message received within " + timeout);
            }
            return message;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while awaiting WebSocket message", e);
        }
    }

    /** Await the next message that satisfies {@code predicate} (parsed as JSON). */
    public JsonPath awaitMessage(Predicate<JsonPath> predicate, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        StringBuilder seen = new StringBuilder();
        while (System.nanoTime() < deadline) {
            long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            try {
                String raw = messages.poll(Math.max(1, remaining), TimeUnit.MILLISECONDS);
                if (raw == null) {
                    break;
                }
                seen.append('\n').append(raw);
                JsonPath json = JsonPath.from(raw);
                if (predicate.test(json)) {
                    return json;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("no matching WebSocket message within " + timeout + "; saw:" + seen);
    }

    @Override
    public void close() {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete");
    }
}
