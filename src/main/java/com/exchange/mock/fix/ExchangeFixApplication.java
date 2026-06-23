package com.exchange.mock.fix;

import com.exchange.mock.api.OrderView;
import com.exchange.mock.api.PlaceOrderRequest;
import com.exchange.mock.api.TradeView;
import com.exchange.mock.domain.Order;
import com.exchange.mock.domain.OrderSource;
import com.exchange.mock.event.OrderEvent;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import quickfix.Application;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.MessageCracker;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.UnsupportedMessageType;
import quickfix.field.Account;
import quickfix.field.AvgPx;
import quickfix.field.ClOrdID;
import quickfix.field.CumQty;
import quickfix.field.CxlRejReason;
import quickfix.field.CxlRejResponseTo;
import quickfix.field.ExecID;
import quickfix.field.ExecType;
import quickfix.field.LastPx;
import quickfix.field.LastQty;
import quickfix.field.LeavesQty;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.OrderID;
import quickfix.field.OrderQty;
import quickfix.field.OrigClOrdID;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.Text;

/**
 * FIX 4.4 view of the exchange. Accepts {@code NewOrderSingle} (35=D) and {@code OrderCancelRequest}
 * (35=F), routes them through the shared {@link com.exchange.mock.service.ExchangeService}, and
 * emits {@code ExecutionReport} (35=8) / {@code OrderCancelReject} (35=9) responses.
 *
 * <p>Responses are driven by the same {@link OrderEvent} stream the WebSocket uses, so a FIX order
 * resting on the book and later filled by a REST order still receives its asynchronous
 * {@code ExecutionReport}. {@code clOrdId → session} is recorded <em>before</em> the order is
 * submitted so synchronous fill events can be routed back to the right session.
 */
@Component
public class ExchangeFixApplication extends MessageCracker implements Application {

    private static final Logger log = LoggerFactory.getLogger(ExchangeFixApplication.class);

    private final com.exchange.mock.service.ExchangeService exchange;
    private final ConcurrentHashMap<String, SessionID> sessionByClOrdId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> orderIdByClOrdId = new ConcurrentHashMap<>();
    private final AtomicLong execSequence = new AtomicLong();

    public ExchangeFixApplication(com.exchange.mock.service.ExchangeService exchange) {
        this.exchange = exchange;
    }

    // ----- Inbound order entry -------------------------------------------------------------------

    public void onMessage(quickfix.fix44.NewOrderSingle message, SessionID sessionId)
            throws FieldNotFound {
        String clOrdId = message.getClOrdID().getValue();
        String symbol = message.getSymbol().getValue();
        char sideChar = message.getSide().getValue();
        char ordTypeChar = message.getOrdType().getValue();
        // Read quantities/prices as raw strings to preserve exact decimals (no double rounding).
        BigDecimal quantity = new BigDecimal(message.getString(OrderQty.FIELD));
        BigDecimal price = ordTypeChar == OrdType.LIMIT
                ? new BigDecimal(message.getString(Price.FIELD)) : null;
        String accountId = message.isSetField(Account.FIELD)
                ? message.getString(Account.FIELD) : "ACC-1";

        com.exchange.mock.domain.Side side =
                sideChar == Side.BUY ? com.exchange.mock.domain.Side.BUY : com.exchange.mock.domain.Side.SELL;
        com.exchange.mock.domain.OrderType type = ordTypeChar == OrdType.MARKET
                ? com.exchange.mock.domain.OrderType.MARKET : com.exchange.mock.domain.OrderType.LIMIT;

        // Record routing before submitting: fills are published synchronously inside placeOrder.
        sessionByClOrdId.put(clOrdId, sessionId);
        PlaceOrderRequest request = new PlaceOrderRequest(accountId, clOrdId, symbol, side, type, price, quantity);
        try {
            Order placed = exchange.placeOrder(request, OrderSource.FIX);
            orderIdByClOrdId.put(clOrdId, placed.id());
        } catch (RuntimeException e) {
            // Pre-trade validation failure (bad symbol, missing price, unknown account, duplicate id).
            sessionByClOrdId.remove(clOrdId);
            sendValidationReject(sessionId, clOrdId, symbol, sideChar, e.getMessage());
        }
    }

    public void onMessage(quickfix.fix44.OrderCancelRequest message, SessionID sessionId)
            throws FieldNotFound {
        String origClOrdId = message.getOrigClOrdID().getValue();
        String cancelClOrdId = message.getClOrdID().getValue();
        String orderId = orderIdByClOrdId.get(origClOrdId);
        try {
            if (orderId == null) {
                throw new com.exchange.mock.error.OrderNotFoundException("unknown OrigClOrdID: " + origClOrdId);
            }
            exchange.cancelOrder(orderId); // CANCELLED event drives the ExecutionReport
        } catch (RuntimeException e) {
            sendCancelReject(sessionId, cancelClOrdId, origClOrdId, orderId, e.getMessage());
        }
    }

    // ----- Outbound: event-driven ExecutionReports ----------------------------------------------

    @EventListener
    public void onOrderEvent(OrderEvent event) {
        if (event.source() != OrderSource.FIX) {
            return;
        }
        SessionID sessionId = sessionByClOrdId.get(event.order().clientOrderId());
        if (sessionId == null) {
            return;
        }
        try {
            Session.sendToTarget(buildExecutionReport(event), sessionId);
        } catch (SessionNotFound e) {
            log.warn("Cannot send ExecutionReport, session not found: {}", sessionId);
        }
    }

    private quickfix.fix44.ExecutionReport buildExecutionReport(OrderEvent event) {
        OrderView o = event.order();
        char execType;
        char ordStatus;
        switch (event.type()) {
            case ACCEPTED -> { execType = ExecType.NEW; ordStatus = OrdStatus.NEW; }
            case PARTIALLY_FILLED -> { execType = ExecType.TRADE; ordStatus = OrdStatus.PARTIALLY_FILLED; }
            case FILLED -> { execType = ExecType.TRADE; ordStatus = OrdStatus.FILLED; }
            case CANCELLED -> { execType = ExecType.CANCELED; ordStatus = OrdStatus.CANCELED; }
            case REJECTED -> { execType = ExecType.REJECTED; ordStatus = OrdStatus.REJECTED; }
            default -> throw new IllegalStateException("unhandled event type " + event.type());
        }
        char sideChar = o.side() == com.exchange.mock.domain.Side.BUY ? Side.BUY : Side.SELL;
        TradeView trade = event.trade();
        double avgPx = trade != null ? trade.price().doubleValue() : 0.0;

        quickfix.fix44.ExecutionReport report = new quickfix.fix44.ExecutionReport(
                new OrderID(o.orderId()),
                new ExecID("E-" + execSequence.incrementAndGet()),
                new ExecType(execType),
                new OrdStatus(ordStatus),
                new Side(sideChar),
                new LeavesQty(o.remainingQuantity().doubleValue()),
                new CumQty(o.filledQuantity().doubleValue()),
                new AvgPx(avgPx));
        report.set(new ClOrdID(o.clientOrderId()));
        report.set(new Symbol(o.symbol()));
        report.set(new OrderQty(o.quantity().doubleValue()));
        report.set(new Account(o.accountId()));
        if (trade != null) {
            report.set(new LastQty(trade.quantity().doubleValue()));
            report.set(new LastPx(trade.price().doubleValue()));
        }
        if (event.type() == com.exchange.mock.event.OrderEventType.REJECTED && o.rejectReason() != null) {
            report.set(new Text(o.rejectReason()));
        }
        return report;
    }

    private void sendValidationReject(SessionID sessionId, String clOrdId, String symbol,
                                      char sideChar, String reason) {
        quickfix.fix44.ExecutionReport report = new quickfix.fix44.ExecutionReport(
                new OrderID("NONE"),
                new ExecID("E-" + execSequence.incrementAndGet()),
                new ExecType(ExecType.REJECTED),
                new OrdStatus(OrdStatus.REJECTED),
                new Side(sideChar),
                new LeavesQty(0),
                new CumQty(0),
                new AvgPx(0));
        report.set(new ClOrdID(clOrdId));
        report.set(new Symbol(symbol));
        report.set(new Text(reason));
        try {
            Session.sendToTarget(report, sessionId);
        } catch (SessionNotFound e) {
            log.warn("Cannot send reject, session not found: {}", sessionId);
        }
    }

    private void sendCancelReject(SessionID sessionId, String cancelClOrdId, String origClOrdId,
                                  String orderId, String reason) {
        quickfix.fix44.OrderCancelReject reject = new quickfix.fix44.OrderCancelReject(
                new OrderID(orderId == null ? "NONE" : orderId),
                new ClOrdID(cancelClOrdId),
                new OrigClOrdID(origClOrdId),
                new OrdStatus(OrdStatus.REJECTED),
                new CxlRejResponseTo(CxlRejResponseTo.ORDER_CANCEL_REQUEST));
        reject.set(new CxlRejReason(CxlRejReason.OTHER));
        reject.set(new Text(reason));
        try {
            Session.sendToTarget(reject, sessionId);
        } catch (SessionNotFound e) {
            log.warn("Cannot send cancel reject, session not found: {}", sessionId);
        }
    }

    // ----- Plumbing ------------------------------------------------------------------------------

    @Override
    public void fromApp(Message message, SessionID sessionId)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        crack(message, sessionId);
    }

    @Override
    public void onCreate(SessionID sessionId) {
        log.info("FIX session created: {}", sessionId);
    }

    @Override
    public void onLogon(SessionID sessionId) {
        log.info("FIX client logged on: {}", sessionId);
    }

    @Override
    public void onLogout(SessionID sessionId) {
        log.info("FIX client logged out: {}", sessionId);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
    }
}
