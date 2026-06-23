package com.exchange.mock.support;

import com.exchange.mock.domain.Side;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import quickfix.Application;
import quickfix.DefaultMessageFactory;
import quickfix.FieldNotFound;
import quickfix.Initiator;
import quickfix.MemoryStoreFactory;
import quickfix.Message;
import quickfix.SLF4JLogFactory;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;
import quickfix.field.Account;
import quickfix.field.ClOrdID;
import quickfix.field.ExecType;
import quickfix.field.HandlInst;
import quickfix.field.MsgType;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.OrderQty;
import quickfix.field.OrigClOrdID;
import quickfix.field.Price;
import quickfix.field.Symbol;
import quickfix.field.TransactTime;

/**
 * A FIX 4.4 initiator that logs on to the mock exchange's acceptor, sends orders/cancels, and
 * captures inbound application messages (ExecutionReport, OrderCancelReject) for assertions.
 */
public class FixTestClient implements AutoCloseable {

    private static final String CONNECT_PORT = "SocketConnectPort";

    private final SessionID sessionId = new SessionID("FIX.4.4", "CLIENT", "EXCHANGE");
    private final BlockingQueue<Message> inbound = new LinkedBlockingQueue<>();
    private final CountDownLatch loggedOn = new CountDownLatch(1);
    private final Initiator initiator;

    public FixTestClient(int port) {
        try (InputStream config = getClass().getClassLoader().getResourceAsStream("quickfixj-client.cfg")) {
            SessionSettings settings = new SessionSettings(config);
            settings.setLong(CONNECT_PORT, port);
            for (Iterator<SessionID> it = settings.sectionIterator(); it.hasNext(); ) {
                settings.setLong(it.next(), CONNECT_PORT, port);
            }
            this.initiator = new SocketInitiator(new CapturingApplication(), new MemoryStoreFactory(),
                    settings, new SLF4JLogFactory(settings), new DefaultMessageFactory());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build FIX test client", e);
        }
    }

    /** Start the initiator and block until the session is logged on. */
    public FixTestClient start() {
        try {
            initiator.start();
            if (!loggedOn.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("FIX client did not log on within 15s");
            }
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start FIX test client", e);
        }
    }

    public void newLimitOrder(String clOrdId, String account, String symbol, Side side,
                              BigDecimal quantity, BigDecimal price) {
        quickfix.fix44.NewOrderSingle order = newOrder(clOrdId, account, symbol, side, OrdType.LIMIT, quantity);
        order.set(new Price(price.doubleValue()));
        send(order);
    }

    public void newMarketOrder(String clOrdId, String account, String symbol, Side side, BigDecimal quantity) {
        send(newOrder(clOrdId, account, symbol, side, OrdType.MARKET, quantity));
    }

    public void cancel(String cancelClOrdId, String origClOrdId, String symbol, Side side) {
        quickfix.fix44.OrderCancelRequest cancel = new quickfix.fix44.OrderCancelRequest(
                new OrigClOrdID(origClOrdId), new ClOrdID(cancelClOrdId), fixSide(side), new TransactTime());
        cancel.set(new Symbol(symbol));
        send(cancel);
    }

    private quickfix.fix44.NewOrderSingle newOrder(String clOrdId, String account, String symbol,
                                                   Side side, char ordType, BigDecimal quantity) {
        quickfix.fix44.NewOrderSingle order = new quickfix.fix44.NewOrderSingle(
                new ClOrdID(clOrdId), fixSide(side), new TransactTime(), new OrdType(ordType));
        order.set(new HandlInst(HandlInst.AUTOMATED_EXECUTION_ORDER_PRIVATE_NO_BROKER_INTERVENTION));
        order.set(new Symbol(symbol));
        order.set(new OrderQty(quantity.doubleValue()));
        order.set(new Account(account));
        return order;
    }

    private void send(Message message) {
        try {
            Session.sendToTarget(message, sessionId);
        } catch (SessionNotFound e) {
            throw new IllegalStateException("FIX session not found: " + sessionId, e);
        }
    }

    /** Await the next inbound message matching {@code predicate}. */
    public Message awaitMessage(Predicate<Message> predicate, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        StringBuilder seen = new StringBuilder();
        while (System.nanoTime() < deadline) {
            long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            try {
                Message message = inbound.poll(Math.max(1, remaining), TimeUnit.MILLISECONDS);
                if (message == null) {
                    break;
                }
                seen.append('\n').append(message);
                if (predicate.test(message)) {
                    return message;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("no matching FIX message within " + timeout + "; saw:" + seen);
    }

    private static quickfix.field.Side fixSide(Side side) {
        return new quickfix.field.Side(side == Side.BUY ? quickfix.field.Side.BUY : quickfix.field.Side.SELL);
    }

    // ----- Field helpers for assertions ----------------------------------------------------------

    public static boolean isExecutionReport(Message m) {
        return isType(m, "8"); // ExecutionReport = 35=8
    }

    public static boolean isOrderCancelReject(Message m) {
        return isType(m, "9");
    }

    public static boolean isType(Message m, String msgType) {
        try {
            return msgType.equals(m.getHeader().getString(MsgType.FIELD));
        } catch (FieldNotFound e) {
            return false;
        }
    }

    public static char execType(Message m) {
        return charField(m, ExecType.FIELD);
    }

    public static char ordStatus(Message m) {
        return charField(m, OrdStatus.FIELD);
    }

    public static String clOrdId(Message m) {
        return stringField(m, ClOrdID.FIELD);
    }

    public static String string(Message m, int tag) {
        return stringField(m, tag);
    }

    public static double field(Message m, int tag) {
        try {
            return m.getDouble(tag);
        } catch (FieldNotFound e) {
            throw new AssertionError("missing FIX field " + tag + " in " + m, e);
        }
    }

    private static char charField(Message m, int tag) {
        try {
            return m.getChar(tag);
        } catch (FieldNotFound e) {
            throw new AssertionError("missing FIX field " + tag + " in " + m, e);
        }
    }

    private static String stringField(Message m, int tag) {
        try {
            return m.getString(tag);
        } catch (FieldNotFound e) {
            throw new AssertionError("missing FIX field " + tag + " in " + m, e);
        }
    }

    @Override
    public void close() {
        initiator.stop();
    }

    private final class CapturingApplication implements Application {
        @Override
        public void onLogon(SessionID sessionId) {
            loggedOn.countDown();
        }

        @Override
        public void fromApp(Message message, SessionID sessionId) {
            inbound.add(message);
        }

        @Override public void onCreate(SessionID sessionId) { }
        @Override public void onLogout(SessionID sessionId) { }
        @Override public void toAdmin(Message message, SessionID sessionId) { }
        @Override public void fromAdmin(Message message, SessionID sessionId) { }
        @Override public void toApp(Message message, SessionID sessionId) { }
    }
}
