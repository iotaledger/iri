package com.iota.iri.utils;

import com.iota.iri.utils.funcinterfaces.RunnableThrows;

import java.time.Duration;

public class ScheduledTask implements Shutdown {

    private final Duration period;

    private final RunnableThrows runnable;

    private long timer = 0L;
    private volatile boolean shutdown;

    public ScheduledTask(Duration period, RunnableThrows runnable) {
        this.period = period;
        this.runnable = runnable;
        this.timer = System.currentTimeMillis();
    }

    public ScheduledTask(Duration period) {
        this(period, null);
    }

    public void run() throws Exception {
        if (shutdown || (System.currentTimeMillis() - timer) <= period.toMillis()) {
            return;
        }
        try {
            if (runnable == null) {
                task();
            } else {
                runnable.run();
            }
        } finally {
            timer = System.currentTimeMillis();
        }

    }

    public void task() throws Exception {
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public void shutdown() {
        shutdown = true;

    }
}
