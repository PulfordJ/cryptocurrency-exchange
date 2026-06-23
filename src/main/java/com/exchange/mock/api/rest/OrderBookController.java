package com.exchange.mock.api.rest;

import com.exchange.mock.api.OrderBookView;
import com.exchange.mock.engine.MatchingEngine;
import com.exchange.mock.service.ExchangeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only order book snapshots. */
@RestController
public class OrderBookController {

    private final ExchangeService exchange;

    public OrderBookController(ExchangeService exchange) {
        this.exchange = exchange;
    }

    @GetMapping("/api/orderbook/{symbol}")
    public OrderBookView snapshot(@PathVariable String symbol,
                                  @RequestParam(defaultValue = "" + MatchingEngine.DEFAULT_DEPTH) int depth) {
        return exchange.orderBook(symbol, depth);
    }
}
