package com.exchange.mock.support;

import static io.restassured.RestAssured.given;

import com.exchange.mock.service.ExchangeService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for all integration tests. Boots the full application (REST + WebSocket + FIX) on random
 * ports and resets exchange state before each test so cases are independent and order-insensitive.
 *
 * <p>The FIX acceptor binds a single free port chosen once per JVM; because every test class shares
 * this base (and therefore the same dynamic property), Spring caches and reuses one application
 * context — one acceptor — across the whole suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    protected static final int FIX_PORT = TestPorts.free();

    @LocalServerPort
    protected int httpPort;

    @Autowired
    protected ExchangeService exchange;

    @DynamicPropertySource
    static void fixProperties(DynamicPropertyRegistry registry) {
        registry.add("exchange.fix.port", () -> FIX_PORT);
    }

    @BeforeEach
    void resetExchangeAndRestAssured() {
        RestAssured.port = httpPort;
        RestAssured.basePath = "/";
        exchange.reset();
    }

    // ----- Shared REST helpers -------------------------------------------------------------------

    protected Response placeOrder(Map<String, Object> body) {
        return given().contentType(ContentType.JSON).body(body).post("/api/orders");
    }

    /** Place an order expecting a 201 and return its server-assigned id. */
    protected String placeOrderId(Map<String, Object> body) {
        return placeOrder(body).then().statusCode(201).extract().path("orderId");
    }

    protected Response getOrder(String orderId) {
        return given().get("/api/orders/" + orderId);
    }

    protected Response cancelOrder(String orderId) {
        return given().delete("/api/orders/" + orderId);
    }

    protected Response orderBook(String symbol) {
        return given().get("/api/orderbook/" + symbol);
    }

    protected Response account(String accountId) {
        return given().get("/api/accounts/" + accountId);
    }
}
