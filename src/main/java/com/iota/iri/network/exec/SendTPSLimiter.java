package com.iota.iri.network.exec;

import com.iota.iri.utils.funcinterfaces.RunnableThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class SendTPSLimiter {

    private static final Logger log = LoggerFactory.getLogger(SendTPSLimiter.class);

    private final Object lock = new Object();

    private final long tps;
    private final String logMessage;

    private long sendPacketsCounter;
    private long sendPacketsTimer;

    public SendTPSLimiter(long tps) {
        this(tps, null);
    }

    public SendTPSLimiter(long tps, String logMessage) {
        if (tps <= 0) {
            throw new IllegalArgumentException("TPS must be a positive non-zero number: " + tps);
        }
        this.tps = tps;

        // trim to null && if null don't use logging
        logMessage = Objects.toString(logMessage, "").trim();
        if (logMessage.length() == 0) {
            logMessage = null;
        }
        this.logMessage = logMessage;
        sendPacketsTimer = System.currentTimeMillis();
    }

    private void resetEverySecond() {
        long now = System.currentTimeMillis();
        if ((now - sendPacketsTimer) > 1000L) {
            sendPacketsCounter = 0;
            sendPacketsTimer = now;
        }
    }

    /**
     * @return false if the send should be dropped
     */
    public boolean isSend() {
        synchronized (lock) {
            //reset counter every second
            resetEverySecond();
            //limit amount of sends per second
            if (sendPacketsCounter > tps) {
                if (logMessage != null) {
                    log.info(logMessage, sendPacketsCounter);
                }
                return false;
            } else {
                return true;
            }
        }
    }


    public void run(Runnable r) {
        synchronized (lock) {
            if (isSend()) {
                sendPacketsCounter++;
                r.run();
            }
        }
    }

    public void runThrower(RunnableThrows r) throws Exception {
        synchronized (lock) {
            if (isSend()) {
                sendPacketsCounter++;
                r.run();
            }
        }
    }

    public void addSent() {
        synchronized (lock) {
            sendPacketsCounter++;
        }
    }
}
