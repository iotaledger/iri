package com.iota.iri.hash;

import java.util.ArrayList;
import java.util.List;

import static com.iota.iri.hash.PearlDiver.State.*;

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

    public void cancel() {
        synchronized (syncObj) {
            state = CANCELLED;
        }
    }

    private static void validateParameters(int[] transactionTrits, int minWeightMagnitude) {
        if (transactionTrits.length != TRANSACTION_LENGTH) {
            throw new RuntimeException(
                "Invalid transaction trits length: " + transactionTrits.length);
        }
        if (minWeightMagnitude < 0 || minWeightMagnitude > CURL_HASH_LENGTH) {
            throw new RuntimeException("Invalid min weight magnitude: " + minWeightMagnitude);
        }
    }

    public synchronized boolean search(final int[] transactionTrits, final int minWeightMagnitude,
                                       int numberOfThreads) {

        validateParameters(transactionTrits, minWeightMagnitude);
        synchronized (syncObj) {
            state = RUNNING;
        }

        final long[] midCurlStateLow = new long[CURL_STATE_LENGTH];
        final long[] midCurlStateHigh = new long[CURL_STATE_LENGTH];
        initializeMidCurlStates(transactionTrits, midCurlStateLow, midCurlStateHigh);

        if (numberOfThreads <= 0) {
            numberOfThreads = Math.max(1, Math.floorDiv(numberOfThreads * 8, 10));
        }
        List<Thread> workers = new ArrayList<>(numberOfThreads);
        while (numberOfThreads-- > 0) {
            Runnable runnable = getRunnable(transactionTrits, minWeightMagnitude, midCurlStateLow, midCurlStateHigh, numberOfThreads);
            Thread worker = new Thread(runnable);
            workers.add(worker);
            worker.setName(this + ":worker-" + numberOfThreads);
            worker.setDaemon(true);
            worker.start();
        }
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                synchronized (syncObj) {
                    state = CANCELLED;
                }
            }
        }
        return state == COMPLETED;
    }

    private Runnable getRunnable(int[] transactionTrits, int minWeightMagnitude, long[] midCurlStateLow, long[] midCurlStateHigh, int threadIndex) {
        return () -> {
            final long[] midCurlStateCopyLow = new long[CURL_STATE_LENGTH];
            final long[] midCurlStateCopyHigh = new long[CURL_STATE_LENGTH];
            copy(midCurlStateLow, midCurlStateHigh, midCurlStateCopyLow, midCurlStateCopyHigh);

            for (int i = 0; i < threadIndex; i++) {
                increment(midCurlStateCopyLow, midCurlStateCopyHigh, 162 + CURL_HASH_LENGTH / 9,
                    162 + (CURL_HASH_LENGTH / 9) * 2);
            }

            final long[] curlStateLow = new long[CURL_STATE_LENGTH];
            final long[] curlStateHigh = new long[CURL_STATE_LENGTH];

            final long[] curlScratchpadLow = new long[CURL_STATE_LENGTH];
            final long[] curlScratchpadHigh = new long[CURL_STATE_LENGTH];

            final int maskStartIndex = CURL_HASH_LENGTH - minWeightMagnitude;
            long mask = 0;
            while (state == RUNNING && mask == 0) {

                increment(midCurlStateCopyLow, midCurlStateCopyHigh, 162 + (CURL_HASH_LENGTH / 9) * 2,
                    CURL_HASH_LENGTH);

                copy(midCurlStateCopyLow, midCurlStateCopyHigh, curlStateLow, curlStateHigh);
                transform(curlStateLow, curlStateHigh, curlScratchpadLow, curlScratchpadHigh);

                mask = HIGH_BITS;
                for (int i = maskStartIndex; i < CURL_HASH_LENGTH && mask != 0; i++) {
                    mask &= ~(curlStateLow[i] ^ curlStateHigh[i]);
                }
            }
            if (mask != 0) {
                synchronized (syncObj) {
                    if (state == RUNNING) {
                        state = COMPLETED;
                        long outMask = 1;
                        while ((outMask & mask) == 0) {
                            outMask <<= 1;
                        }
                        for (int i = 0; i < CURL_HASH_LENGTH; i++) {
                            transactionTrits[TRANSACTION_LENGTH - CURL_HASH_LENGTH + i] =
                                (midCurlStateCopyLow[i] & outMask) == 0 ? 1
                                    : (midCurlStateCopyHigh[i] & outMask) == 0 ? -1 : 0;
                        }
                    }
                }
            }
        };
    }

    private static void copy(long[] srcLow, long[] srcHigh, long[] destLow, long[] destHigh) {
        System.arraycopy(srcLow, 0, destLow, 0, CURL_STATE_LENGTH);
        System.arraycopy(srcHigh, 0, destHigh, 0, CURL_STATE_LENGTH);
    }

    private static void initializeMidCurlStates(int[] transactionTrits, long[] midCurlStateLow, long[] midCurlStateHigh) {
        for (int i = CURL_HASH_LENGTH; i < CURL_STATE_LENGTH; i++) {
            midCurlStateLow[i] = HIGH_BITS;
            midCurlStateHigh[i] = HIGH_BITS;
        }

        int offset = 0;
        final long[] curlScratchpadLow = new long[CURL_STATE_LENGTH];
        final long[] curlScratchpadHigh = new long[CURL_STATE_LENGTH];
        for (int i = (TRANSACTION_LENGTH - CURL_HASH_LENGTH) / CURL_HASH_LENGTH; i-- > 0; ) {

            for (int j = 0; j < CURL_HASH_LENGTH; j++) {
                switch (transactionTrits[offset++]) {
                    case 0:
                        midCurlStateLow[j] = HIGH_BITS;
                        midCurlStateHigh[j] = HIGH_BITS;
                        break;
                    case 1:
                        midCurlStateLow[j] = LOW_BITS;
                        midCurlStateHigh[j] = HIGH_BITS;
                        break;
                    default:
                        midCurlStateLow[j] = HIGH_BITS;
                        midCurlStateHigh[j] = LOW_BITS;
                }
            }
            transform(midCurlStateLow, midCurlStateHigh, curlScratchpadLow, curlScratchpadHigh);
        }

        for (int i = 0; i < 162; i++) {
            switch (transactionTrits[offset++]) {
                case 0:
                    midCurlStateLow[i] = HIGH_BITS;
                    midCurlStateHigh[i] = HIGH_BITS;
                    break;
                case 1:
                    midCurlStateLow[i] = LOW_BITS;
                    midCurlStateHigh[i] = HIGH_BITS;
                    break;
                default:
                    midCurlStateLow[i] = HIGH_BITS;
                    midCurlStateHigh[i] = LOW_BITS;
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

    private static void transform(final long[] curlStateLow, final long[] curlStateHigh,
                                  final long[] curlScratchpadLow, final long[] curlScratchpadHigh) {

        for (int round = 0; round < Curl.NUMBER_OF_ROUNDSP81; round++) {
            copy(curlStateLow, curlStateHigh, curlScratchpadLow, curlScratchpadHigh);

            for (int curlStateIndex = 0, curlScratchpadIndex = 0; curlStateIndex < CURL_STATE_LENGTH; curlStateIndex++) {
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

    private static void increment(final long[] midCurlStateCopyLow, final long[] midCurlStateCopyHigh,
                                  final int fromIndex, final int toIndex) {

        for (int i = fromIndex; i < toIndex; i++) {
            if (midCurlStateCopyLow[i] == LOW_BITS) {
                midCurlStateCopyLow[i] = HIGH_BITS;
                midCurlStateCopyHigh[i] = LOW_BITS;
            } else if (midCurlStateCopyHigh[i] == LOW_BITS) {
                midCurlStateCopyHigh[i] = HIGH_BITS;
                break;
            } else {
                midCurlStateCopyLow[i] = LOW_BITS;
                break;
            }
        }
    }
}