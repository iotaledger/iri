package com.iota.iri.hash;

import static com.iota.iri.hash.PearlDiver.State.CANCELLED;
import static com.iota.iri.hash.PearlDiver.State.COMPLETED;
import static com.iota.iri.hash.PearlDiver.State.RUNNING;

/**
 * (c) 2016 Come-from-Beyond
 */
public class PearlDiver {

    enum State {
        RUNNING,
        CANCELLED,
        COMPLETED
    }

    private static final int TRANSACTION_LENGTH = 8019;

    private static final int CURL_HASH_LENGTH = 243;
    private static final int CURL_STATE_LENGTH = CURL_HASH_LENGTH * 3;

    private static final long HIGH_BITS = 0b11111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L;
    private static final long LOW_BITS = 0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L;

    private volatile State state;
    private final Object syncObj = new Object();

    public class Pow implements Runnable {
        private final long[] midCurlStateLow;
        private final long[] midCurlStateHigh;
        private final int[] transactionTrits;
        private final int mwm;

        public Pow(final long[] midCurlStateLow, final long[] midCurlStateHigh, final int[] transactionTrits, final int mwm) {
            this.midCurlStateLow = midCurlStateLow;
            this.midCurlStateHigh = midCurlStateHigh;
            this.transactionTrits = transactionTrits;
            this.mwm = mwm;
        }

        @Override
        public void run() {
            long mask;
            long outMask = 1;

            if ((mask = PearlDiver.this.loop(this.midCurlStateLow, this.midCurlStateHigh, this.mwm)) != 0 &&
                state == RUNNING) {
                synchronized (syncObj) {
                    state = COMPLETED;

                    while ((outMask & mask) == 0) {
                        outMask <<= 1;
                    }

                    for (int i = 0; i < CURL_HASH_LENGTH; i++) {
                        this.transactionTrits[TRANSACTION_LENGTH - CURL_HASH_LENGTH + i] =
                            (this.midCurlStateLow[i] & outMask) == 0 ? 1
                            : (this.midCurlStateHigh[i] & outMask) == 0 ? -1 : 0;
                    }
                }
            }
        }
    }

    public void cancel() {
        if (state == RUNNING) {
            synchronized (syncObj) {
                state = CANCELLED;
            }
        }
    }

    public synchronized boolean search(final int[] transactionTrits, final int minWeightMagnitude) {

        if (transactionTrits.length != TRANSACTION_LENGTH) {
            throw new RuntimeException(
                "Invalid transaction trits length: " + transactionTrits.length);
        }
        if (minWeightMagnitude < 0 || minWeightMagnitude > CURL_HASH_LENGTH) {
            throw new RuntimeException("Invalid min weight magnitude: " + minWeightMagnitude);
        }

        // state still needs protected, because it is 
        // possible that another thread calls cancel()
        synchronized (syncObj) {
            state = RUNNING;
        }

        final long[] midCurlStateLow = new long[CURL_STATE_LENGTH], midCurlStateHigh = new long[CURL_STATE_LENGTH];
        int numberOfThreads = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        Thread[] workers = new Thread[numberOfThreads];

        para(transactionTrits, midCurlStateLow, midCurlStateHigh);

        while (numberOfThreads-- > 0) {
            final int threadIndex = numberOfThreads;
            final long[] midCurlStateCopyLow = new long[CURL_STATE_LENGTH];
            final long[] midCurlStateCopyHigh = new long[CURL_STATE_LENGTH];

            System.arraycopy(midCurlStateLow, 0, midCurlStateCopyLow, 0, CURL_STATE_LENGTH);
            System.arraycopy(midCurlStateHigh, 0, midCurlStateCopyHigh, 0, CURL_STATE_LENGTH);

            for (int i = threadIndex; i-- > 0; ) {
                increment(midCurlStateCopyLow, midCurlStateCopyHigh, 162 + CURL_HASH_LENGTH / 9,
                        162 + (CURL_HASH_LENGTH / 9) * 2);
            }

            Pow pwork = new Pow(midCurlStateCopyLow, midCurlStateCopyHigh, transactionTrits, minWeightMagnitude);
            workers[threadIndex] = new Thread(pwork);
            workers[threadIndex].start();
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (final InterruptedException e) {
                synchronized (syncObj) {
                    state = CANCELLED;
                }
            }
        }

        return state == COMPLETED;
    }

    private static void para(final int[] transactionTrits, final long[] midCurlStateLow, final long[] midCurlStateHigh) {
        
        for (int i = CURL_HASH_LENGTH; i < CURL_STATE_LENGTH; i++) {
            midCurlStateLow[i] = HIGH_BITS;
            midCurlStateHigh[i] = HIGH_BITS;
        }

        int offset = 0;
        
        for (int i = (TRANSACTION_LENGTH - CURL_HASH_LENGTH) / CURL_HASH_LENGTH; i-- > 0; ) {
            for (int j = 0; j < CURL_HASH_LENGTH; j++) {
                switch (transactionTrits[offset++]) {
                    case 0: {
                        midCurlStateLow[j] = HIGH_BITS;
                        midCurlStateHigh[j] = HIGH_BITS;
                    }
                    break;

                    case 1: {
                        midCurlStateLow[j] = LOW_BITS;
                        midCurlStateHigh[j] = HIGH_BITS;
                    }
                    break;

                    default: {
                        midCurlStateLow[j] = HIGH_BITS;
                        midCurlStateHigh[j] = LOW_BITS;
                    }
                }
            }
            transform(midCurlStateLow, midCurlStateHigh);
        }

        for (int i = 0; i < 162; i++) {
            switch (transactionTrits[offset++]) {
                case 0: {
                    midCurlStateLow[i] = HIGH_BITS;
                    midCurlStateHigh[i] = HIGH_BITS;
                } break;

                case 1: {
                    midCurlStateLow[i] = LOW_BITS;
                    midCurlStateHigh[i] = HIGH_BITS;
                } break;

                default: {
                    midCurlStateLow[i] = HIGH_BITS;
                    midCurlStateHigh[i] = LOW_BITS;
                }
            }
        }

        midCurlStateLow[162 + 0] = 0b1101101101101101101101101101101101101101101101101101101101101101L;
        midCurlStateHigh[162 + 0] = 0b1011011011011011011011011011011011011011011011011011011011011011L;
        midCurlStateLow[162 + 1] = 0b1111000111111000111111000111111000111111000111111000111111000111L;
        midCurlStateHigh[162 + 1] = 0b1000111111000111111000111111000111111000111111000111111000111111L;
        midCurlStateLow[162 + 2] = 0b0111111111111111111000000000111111111111111111000000000111111111L;
        midCurlStateHigh[162 + 2] = 0b1111111111000000000111111111111111111000000000111111111111111111L;
        midCurlStateLow[162 + 3] = 0b1111111111000000000000000000000000000111111111111111111111111111L;
        midCurlStateHigh[162 + 3] = 0b0000000000111111111111111111111111111111111111111111111111111111L;
    }

    private long loop(final long[] midCurlStateLow, final long[] midCurlStateHigh, final int mwm) {
        final long[] curlStateLow = new long[CURL_STATE_LENGTH], curlStateHigh = new long[CURL_STATE_LENGTH];
        long mask = 1;

        while (state == RUNNING) {
            PearlDiver.increment(midCurlStateLow, midCurlStateHigh, 162 + (CURL_HASH_LENGTH / 9) * 2, CURL_HASH_LENGTH);
            System.arraycopy(midCurlStateLow, 0, curlStateLow, 0, CURL_STATE_LENGTH);
            System.arraycopy(midCurlStateHigh, 0, curlStateHigh, 0, CURL_STATE_LENGTH);
            PearlDiver.transform(curlStateLow, curlStateHigh);
            mask = HIGH_BITS;

            for (int i = mwm; i-- > 0; ) {
                mask &= ~(curlStateLow[CURL_HASH_LENGTH - 1 - i] ^ curlStateHigh[CURL_HASH_LENGTH - 1 - i]);
                if (mask == 0) break;
            }
            if (mask != 0) break;
        }
        return mask;
    } 

    private static void transform(final long[] curlStateLow, final long[] curlStateHigh) {
        int curlScratchpadIndex = 0;
        final long[] curlScratchpadLow = new long[CURL_STATE_LENGTH], curlScratchpadHigh = new long[CURL_STATE_LENGTH];

        for (int round = 0; round < Curl.NUMBER_OF_ROUNDSP81; round++) {
            System.arraycopy(curlStateLow, 0, curlScratchpadLow, 0, CURL_STATE_LENGTH);
            System.arraycopy(curlStateHigh, 0, curlScratchpadHigh, 0, CURL_STATE_LENGTH);

            for (int curlStateIndex = 0; curlStateIndex < CURL_STATE_LENGTH; curlStateIndex++) {
                final long alpha = curlScratchpadLow[curlScratchpadIndex];
                final long beta = curlScratchpadHigh[curlScratchpadIndex];
                if (curlScratchpadIndex < 365) {
                    curlScratchpadIndex += 364;
                } else {
                    curlScratchpadIndex += -365;
                }
                final long gamma = curlScratchpadHigh[curlScratchpadIndex];
                final long delta = (alpha | (~gamma)) & (curlScratchpadLow[curlScratchpadIndex] ^ beta);

                curlStateLow[curlStateIndex] = ~delta;
                curlStateHigh[curlStateIndex] = (alpha ^ gamma) | delta;
            }
        }
    }

    private static void increment(final long[] midCurlStateCopyLow,
        final long[] midCurlStateCopyHigh, final int fromIndex, final int toIndex) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (midCurlStateCopyLow[i] == LOW_BITS) {
                midCurlStateCopyLow[i] = HIGH_BITS;
                midCurlStateCopyHigh[i] = LOW_BITS;
            } else {
                if (midCurlStateCopyHigh[i] == LOW_BITS) {
                    midCurlStateCopyHigh[i] = HIGH_BITS;
                } else {
                    midCurlStateCopyLow[i] = LOW_BITS;
                }
                break;
            }
        }
    }
}
