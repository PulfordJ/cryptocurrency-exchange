package com.exchange.mock.api.rest;

import com.exchange.mock.api.AccountView;
import com.exchange.mock.service.ExchangeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Account/funding queries. */
@RestController
public class AccountController {

    private final ExchangeService exchange;

    public AccountController(ExchangeService exchange) {
        this.exchange = exchange;
    }

    @GetMapping("/api/accounts/{accountId}")
    public AccountView get(@PathVariable String accountId) {
        return AccountView.of(exchange.account(accountId));
    }
}
