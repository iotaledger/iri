package com.iota.iri.network.exec;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public abstract class StripedExecutor<A, B> {
    private static final Logger log = LoggerFactory.getLogger(StripedExecutor.class);

    //  helper - can use to make stripes according to concurrency required
    ////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////
    public static class StripeManager {

        private final int maxStripes;
        private final String stripeGroup;
        private final String[] stripeLabels;
        private AtomicInteger stripe;

        public StripeManager(int maxStripes, String group) {
            this.stripeGroup = group;
            this.maxStripes = maxStripes;
            this.stripe = new AtomicInteger(-1);
            this.stripeLabels = new String[this.maxStripes];
            IntStream.range(0, maxStripes).forEach(n -> stripeLabels[n] = (group + n));
        }

        public String stripe() {
            int n = stripe.incrementAndGet();
            if (stripe.compareAndSet(maxStripes, 0)) {
                n = 0;
            }
            return stripeLabels[n];
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////


    private final Map<String, Stat> stripeCounts = new ConcurrentHashMap<>();
    private final ExecutorService executorForJobsWaiting = new StripedExecutorService();
    private final long startTime = System.currentTimeMillis();

    private static final class Stat {
        AtomicLong queued = new AtomicLong(0);
        AtomicLong completed = new AtomicLong(0);
    }


    public long getJobsQueuedCount() {
        long cnt = 0L;
        for (Stat stat : stripeCounts.values()) {
            cnt += stat.queued.get();
            cnt -= stat.completed.get();
        }
        return cnt;
    }

    public ExecutorService getExecutorForJobsWaiting() {
        return executorForJobsWaiting;
    }


    public abstract void process(A a, B b);


    @SuppressWarnings("unchecked")
    public <T> Future<T> submitStripe(String stripe, FutureTask<T> task) {
        return (Future<T>) submitStripe(stripe, (Runnable) task);
    }

    @SuppressWarnings("unchecked")
    public <T> Future<T> submitStripe(String stripe, Callable<T> task) {
        return (Future<T>) submitStripe(stripe, (Runnable) new FutureTask<T>(task));
    }


    public Future<?> submitStripe(final String stripe, final Runnable task) {
        Stat stat = stripeCounts.computeIfAbsent(stripe, k -> {
            log.debug("New: {}", stripe);
            return new Stat();
        });
        stat.queued.incrementAndGet();
        return executorForJobsWaiting.submit(new StripedRunnable() {
            @Override
            public Object getStripe() {
                return stripe;
            }

            @Override
            public void run() {
                stat.completed.incrementAndGet();
                task.run();
            }
        });
    }


    public void report() {
        long[] totalQueued = {0L};
        long[] totalCompleted = {0L};
        stripeCounts.entrySet()
                .stream()
                .sequential()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(n -> {
                    long queued = n.getValue().queued.get();
                    long completed = n.getValue().completed.get();
                    totalQueued[0] += queued;
                    totalCompleted[0] += completed;

                    log.info("    {} jobs  {} pending  '{}'",
                            StringUtils.leftPad(String.valueOf(queued), 7),
                            StringUtils.leftPad(String.valueOf(queued - completed), 7),
                            n.getKey());
                });

        long pending = totalQueued[0] - totalCompleted[0];
        Duration uptime = Duration.ofMillis(System.currentTimeMillis()).minusMillis(startTime);

        log.info("TOT {} jobs  {} pending  {} uptime  {}  tps",
                StringUtils.leftPad(String.valueOf(totalQueued[0]), 7),
                StringUtils.leftPad(String.valueOf(pending), 7),
                StringUtils.leftPad(StringUtils.substringAfter(uptime.toString(), "PM"), 6),
                StringUtils.leftPad(String.valueOf((int) (totalCompleted[0] / uptime.getSeconds())), 4));
        ;
    }

    public void shutdown() throws InterruptedException {
        executorForJobsWaiting.shutdown();
        executorForJobsWaiting.awaitTermination(6, TimeUnit.SECONDS);
    }
}
