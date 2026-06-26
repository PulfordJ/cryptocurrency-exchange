# Mock Cryptocurrency Exchange + Java Integration Test Suite

A lightweight mock of a crypto exchange and a suite of Java automated integration tests against it.

The mock exposes the **same matching engine over three protocols** so that the same trading
behaviour can be driven and observed through whichever channel a real venue would use:

| Protocol      | Endpoint                                   | Used for                                   |
|---------------|--------------------------------------------|--------------------------------------------|
| **REST**      | `http://localhost:8080/api/...`            | Order management, queries, balances        |
| **WebSocket** | `ws://localhost:8080/ws/marketdata`, `/ws/orders` | Streaming order book + async order events |
| **FIX 4.4**   | `tcp://localhost:9876` (QuickFIX/J acceptor) | Order execution (`NewOrderSingle` → `ExecutionReport`) |

The headline test ([`TradingScenarioE2ETest`](src/test/java/com/exchange/mock/e2e/TradingScenarioE2ETest.java))
rests an order via **FIX**, fills it via **REST**, and asserts the fill is observed consistently on
**FIX, WebSocket and REST** at once — proving all three transports stay in sync because they share
one engine.

---

## Local dev

| Path | Requires |
|------|----------|
| [Nix + terminal](#nix--terminal) | Nix |
| [Nix + VS Code](#nix--vs-code) | Nix, VS Code |
| [JDK 21 + terminal](#jdk-21--terminal) | JDK 21 |
| [Docker + VS Code Dev Container](#docker--vs-code-dev-container) | Docker, VS Code |

### Nix + terminal

The environment is fully reproducible via **Nix** (JDK 21 + Gradle are pinned in `flake.nix`); nothing
needs to be installed globally. If you don't have Nix, see [PulfordJ/install-nix](https://github.com/PulfordJ/install-nix).

```bash
# Enter the reproducible toolchain (JDK 21 + Gradle from Nix)
nix develop

# Run the full integration test suite (REST + WebSocket + FIX + cross-protocol E2E)
./gradlew test

# Run the mock exchange (REST/WebSocket on :8080, FIX acceptor on :9876)
./gradlew bootRun
```

Or as one-liners without entering the shell:

```bash
nix develop --command ./gradlew test
nix develop --command ./gradlew bootRun
```

> The committed `./gradlew` wrapper pins Gradle 8.14.4; `nix develop` exports `JAVA_HOME` so the
> wrapper runs on the Nix-provided JDK 21.

---

### Nix + VS Code

Launch VS Code from inside the Nix shell so it inherits `JAVA_HOME` and the pinned JDK — the Java
and Gradle extensions resolve correctly without any manual SDK configuration.

**WSL (Windows)**

```bash
# In a WSL terminal (e.g. Windows Terminal → Ubuntu)
cd /path/to/cryptocurrency-exchange
nix develop
code .
```

VS Code opens on Windows with the WSL remote extension connected to the Nix environment. The first `code .` from WSL may prompt you to install the **WSL** extension — do so, then re-run `code .`.

**macOS / Linux**

```bash
cd /path/to/cryptocurrency-exchange
nix develop
code .
```

---

### JDK 21 + terminal

If you already have JDK 21 on your `PATH`, no other tooling is needed:

```bash
./gradlew test
./gradlew bootRun
```

The committed `./gradlew` wrapper pins Gradle 8.14.4 and downloads it automatically on first run.

---

### Docker + VS Code Dev Container

No local JDK or Nix required — JDK 21 is baked into the devcontainer image. Open the repo in
VS Code with the **Dev Containers** extension installed and choose **Reopen in Container**.

Three definitions live under [`.devcontainer/`](.devcontainer/):

- **`devcontainer.json`** — default (macOS/Linux hosts)
- **`wsl/devcontainer.json`** — Windows + WSL2 host paths *(edit the hard-coded `\\wsl.localhost\Ubuntu\home\<you>\…` mount paths for your user)*
- **`windows/devcontainer.json`** — native Windows host paths

On create the container warms the Gradle cache; `supervisord` then auto-starts the mock on ports 8080/9876.

---

## Troubleshooting

### VS Code cannot find tests but `./gradlew test` runs fine

This is a known bug in newer versions of the **Language Support for Java(TM) by Red Hat** extension.
Downgrading to **1.54.0** fixes it:

1. Open the Extensions view (`Ctrl+Shift+X` / `Cmd+Shift+X`).
2. Search for **Language Support for Java(TM) by Red Hat**, click the gear icon, and choose **Install Specific Version…**.
3. Select **1.54.0**.
4. Reload VS Code.

---

## Trying it manually

```bash
# List supported instruments
curl -s localhost:8080/api/symbols

# Rest a SELL on the book, then aggress it with a BUY
curl -s -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
  -d '{"accountId":"ACC-1","symbol":"BTC-USD","side":"SELL","type":"LIMIT","price":30000,"quantity":1}'
curl -s -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
  -d '{"accountId":"ACC-2","symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":30000,"quantity":1}'

# Inspect the book and an account's balances
curl -s localhost:8080/api/orderbook/BTC-USD
curl -s localhost:8080/api/accounts/ACC-1

# Stream market data (needs a WebSocket client such as websocat / wscat)
websocat "ws://localhost:8080/ws/marketdata?symbol=BTC-USD"
websocat "ws://localhost:8080/ws/orders?accountId=ACC-1"
```

A ready-made FIX client is provided as a test helper
([`FixTestClient`](src/test/java/com/exchange/mock/support/FixTestClient.java)) — see the FIX tests
for usage.

### Seeded accounts & instruments

| Account     | Balances                              | Purpose                          |
|-------------|---------------------------------------|----------------------------------|
| `ACC-1`     | 1,000,000 USD · 100 BTC · 1,000 ETH   | General trading                  |
| `ACC-2`     | 1,000,000 USD · 100 BTC · 1,000 ETH   | Counterparty                     |
| `ACC-EMPTY` | 0 USD · 0 BTC                         | Insufficient-funds testing       |

Instruments: `BTC-USD`, `ETH-USD`, `ETH-BTC`. `POST /api/admin/reset` restores this initial state.

---

## REST API

| Method & path                     | Description                                              |
|-----------------------------------|----------------------------------------------------------|
| `POST /api/orders`                | Place an order. `201` on creation (status may be `REJECTED`). |
| `GET /api/orders/{id}`            | Fetch an order (`404` if unknown).                       |
| `GET /api/orders?accountId=`      | List orders, optionally filtered by account.             |
| `DELETE /api/orders/{id}`         | Cancel an order (`404` unknown, `409` if terminal).      |
| `GET /api/orderbook/{symbol}`     | Aggregated top-of-book snapshot (`?depth=N`).            |
| `GET /api/accounts/{id}`          | Account balances (`available` / `reserved` / `total`).   |
| `GET /api/symbols`                | Listed instruments.                                      |
| `POST /api/admin/reset`           | Reset all state (test isolation).                        |

---

## Design choices

### Mocking strategy — one engine, three protocols
Rather than mock each protocol independently (e.g. with WireMock stubs), the mock runs a **real,
small matching engine** behind a single [`ExchangeService`](src/main/java/com/exchange/mock/service/ExchangeService.java)
facade. REST controllers, the WebSocket handlers and the FIX acceptor all call that facade, and the
engine publishes [domain events](src/main/java/com/exchange/mock/event) that the WebSocket and FIX
layers translate into their own messages. This is what makes cross-protocol consistency testable:
an order entered on FIX and filled on REST genuinely flows through the same state.

```
 REST  ─┐                         ┌─► WebSocket  (/ws/marketdata, /ws/orders)
 FIX   ─┼─► ExchangeService ─► MatchingEngine ─► events ─┤
 (WS)  ─┘        │                    │                  └─► FIX  (ExecutionReport)
                 └─► AccountService (funding / settlement)
```

### Why REST + WebSocket + FIX
A real exchange uses each protocol where it fits: **REST** for request/response order management,
**WebSocket** for push-based market data and async order status, and **FIX** for institutional order
execution. Implementing all three lets the tests verify the *same* behaviour across transports.

### Trading & funding semantics
- **Price-time priority** matching for `LIMIT` and `MARKET` orders; trades print at the **maker's
  price**, so an aggressive limit is refunded any price improvement.
- **`MARKET` orders are immediate-or-cancel**: any unfillable remainder is cancelled (or the order is
  rejected if there is no liquidity at all).
- **Funding**: a limit buy reserves quote (USD), a limit sell reserves base (BTC); cancels release the
  reservation; fills settle both accounts with **exact `BigDecimal`** double-entry bookkeeping (money
  is never represented as `double`).
- **Validation vs. business rejection** — a deliberate, tested distinction:
  - *Structural/validation* problems (bad JSON, missing fields, unlisted symbol, unknown account,
    duplicate `clientOrderId`) → **4xx**, and **no** order is created.
  - *Business* outcomes (insufficient funds, no liquidity) → **201** with a `REJECTED` order resource,
    mirroring how a venue acknowledges then rejects. This is also surfaced on FIX as an
    `ExecutionReport(ExecType=REJECTED)`.
- **Idempotency**: a `clientOrderId` may not be reused for an account (`409 Conflict`).

### Test framework structure
- **JUnit 5 + RestAssured + AssertJ**, with `@SpringBootTest(RANDOM_PORT)` booting the whole app
  (all three protocols) per the shared [`IntegrationTestBase`](src/test/java/com/exchange/mock/support/IntegrationTestBase.java).
- **Two layers (a test pyramid)**: fast, Spring-free unit tests in
  [`unit/`](src/test/java/com/exchange/mock/unit) pin the order book, funding math and
  matching-engine edge cases directly; the integration tests above prove the transports agree
  end-to-end.
- **Deterministic & independent**: every test resets exchange state first; the FIX acceptor binds a
  free port chosen once per JVM so one cached Spring context serves the whole suite.
- **No `Thread.sleep`**: asynchronous WebSocket and FIX assertions poll with bounded timeouts via the
  [`WsTestClient`](src/test/java/com/exchange/mock/support/WsTestClient.java) and
  [`FixTestClient`](src/test/java/com/exchange/mock/support/FixTestClient.java) helpers.

### Risk-based coverage
Testing is prioritised by where financial software fails most expensively — money movement, order
state transitions, and edge/negative cases — not just happy paths:

| Area (risk)                         | Test class                                                                                   |
|-------------------------------------|----------------------------------------------------------------------------------------------|
| Order lifecycle & status codes      | `rest/OrderRestIntegrationTest`                                                              |
| Input validation & edge cases       | `rest/OrderValidationIntegrationTest`                                                       |
| Funds: reserve / release / settle | `rest/AccountFundingIntegrationTest`                                                       |
| Matching correctness                | `engine/MatchingEngineIntegrationTest`                                                       |
| Market-data streaming               | `ws/MarketDataStreamIntegrationTest`                                                         |
| Async order events & filtering      | `ws/OrderEventsStreamIntegrationTest`                                                        |
| FIX execution & cancel/reject       | `fix/FixOrderExecutionIntegrationTest`                                                        |
| Cross-protocol state consistency | `e2e/TradingScenarioE2ETest`                                                                |
| Order book / funding / engine units | `unit/OrderBookTest`, `unit/AccountServiceTest`, `unit/MatchingEngineTest`, `unit/OrderTest` |

### Coverage (JaCoCo)
`./gradlew test` writes a coverage report to `build/reports/jacoco/test/html/index.html` (with an
XML report alongside for CI). `./gradlew jacocoTestCoverageVerification` enforces a 70%
instruction/line floor. Current coverage is ~95% instruction / 94% line — the uncovered remainder
is mostly the Spring bootstrap `main`, which the tests never invoke.

---

## Project layout

```
flake.nix / flake.lock        Reproducible JDK 21 + Gradle toolchain
build.gradle.kts              Spring Boot 3 + QuickFIX/J + test dependencies
.devcontainer/                default / wsl / windows dev containers (+ Dockerfile, supervisord)
src/main/java/com/exchange/mock/
  domain/   engine/   account/   event/        Core: orders, matching, funding, events
  service/  api/ (+ api/rest)  ws/  fix/        Facade, DTOs, REST, WebSocket, FIX
src/main/resources/           application.yml, quickfixj-server.cfg
src/test/java/com/exchange/mock/
  rest/ engine/ ws/ fix/ e2e/                   Integration tests
  support/                                       Test base + WS/FIX clients + fixtures
```

## Notes & limitations
- State is in-memory and resettable — appropriate for a mock; no persistence.
- The FIX wire uses QuickFIX/J's `double`-based price/quantity fields, while all internal accounting
  is exact `BigDecimal`. Inbound FIX quantities/prices are read as raw strings to avoid rounding.
- A single seeded symbol set and account set keep behaviour deterministic for testing.
