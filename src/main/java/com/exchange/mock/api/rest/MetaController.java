package com.exchange.mock.api.rest;

import com.exchange.mock.service.ExchangeService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exchange metadata and test/admin operations. */
@RestController
public class MetaController {

    private final ExchangeService exchange;

    public MetaController(ExchangeService exchange) {
        this.exchange = exchange;
    }

    @GetMapping("/api/symbols")
    public List<String> symbols() {
        return exchange.supportedSymbols().stream().sorted().toList();
    }

    /** Reset all exchange state (orders, books, balances). Intended for test isolation. */
    @PostMapping("/api/admin/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        exchange.reset();
    }
}
