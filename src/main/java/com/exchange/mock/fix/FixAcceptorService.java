package com.exchange.mock.fix;

import java.io.InputStream;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import quickfix.Acceptor;
import quickfix.ConfigError;
import quickfix.DefaultMessageFactory;
import quickfix.MemoryStoreFactory;
import quickfix.MessageStoreFactory;
import quickfix.SLF4JLogFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;

/**
 * Owns the QuickFIX/J acceptor lifecycle, tying it to the Spring context. Settings are loaded from
 * {@code quickfixj-server.cfg} on the classpath; the accept port is overridden from the
 * {@code exchange.fix.port} property so tests can bind an isolated port.
 *
 * <p>Uses in-memory message/sequence stores and SLF4J logging, so running the mock leaves no FIX
 * artefacts on disk.
 */
@Component
public class FixAcceptorService implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(FixAcceptorService.class);
    private static final String CONFIG = "quickfixj-server.cfg";
    private static final String ACCEPT_PORT = "SocketAcceptPort";

    private final ExchangeFixApplication application;
    private final int port;

    private Acceptor acceptor;
    private volatile boolean running;

    public FixAcceptorService(ExchangeFixApplication application,
                              @Value("${exchange.fix.port:9876}") int port) {
        this.application = application;
        this.port = port;
    }

    @Override
    public void start() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG)) {
            if (in == null) {
                throw new IllegalStateException("FIX config not found on classpath: " + CONFIG);
            }
            SessionSettings settings = new SessionSettings(in);
            settings.setLong(ACCEPT_PORT, port);
            for (Iterator<SessionID> it = settings.sectionIterator(); it.hasNext(); ) {
                settings.setLong(it.next(), ACCEPT_PORT, port);
            }

            MessageStoreFactory storeFactory = new MemoryStoreFactory();
            acceptor = new SocketAcceptor(application, storeFactory, settings,
                    new SLF4JLogFactory(settings), new DefaultMessageFactory());
            acceptor.start();
            running = true;
            logger.info("FIX 4.4 acceptor listening on port {}", port);
        } catch (ConfigError | RuntimeException | java.io.IOException e) {
            throw new IllegalStateException("Failed to start FIX acceptor", e);
        }
    }

    @Override
    public void stop() {
        if (acceptor != null) {
            acceptor.stop();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Start after the web server, stop before it. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }
}
