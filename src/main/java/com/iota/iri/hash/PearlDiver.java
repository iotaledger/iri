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

    public void cancel() {
        synchronized (syncObj) {
            state = CANCELLED;
            syncObj.notifyAll();
        }
    }

    public synchronized boolean search(final int[] transactionTrits, final int minWeightMagnitude,
        int numberOfThreads) {

        if (transactionTrits.length != TRANSACTION_LENGTH) {
            throw new RuntimeException(
                "Invalid transaction trits length: " + transactionTrits.length);
        }
        if (minWeightMagnitude < 0 || minWeightMagnitude > CURL_HASH_LENGTH) {
            throw new RuntimeException("Invalid min weight magnitude: " + minWeightMagnitude);
        }

        synchronized (syncObj) {
            state = RUNNING;
        }

        final long[] midCurlStateLow = new long[CURL_STATE_LENGTH], midCurlStateHigh = new long[CURL_STATE_LENGTH];

        {
            for (int i = CURL_HASH_LENGTH; i < CURL_STATE_LENGTH; i++) {
                midCurlStateLow[i] = HIGH_BITS;
                midCurlStateHigh[i] = HIGH_BITS;
            }

            int offset = 0;
            final long[] curlScratchpadLow = new long[CURL_STATE_LENGTH], curlScratchpadHigh = new long[CURL_STATE_LENGTH];
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

                transform(midCurlStateLow, midCurlStateHigh, curlScratchpadLow, curlScratchpadHigh);
            }

            midCurlStateLow[0] = 0b11011011_01101101_10110110_11011011_01101101_10110110_11011011_01101101L;
            midCurlStateHigh[0] = 0b10110110_11011011_01101101_10110110_11011011_01101101_10110110_11011011L;
            midCurlStateLow[1] = 0b11110001_11111000_11111100_01111110_00111111_00011111_10001111_11000111L;
            midCurlStateHigh[1] = 0b10001111_11000111_11100011_11110001_11111000_11111100_01111110_00111111L;
            midCurlStateLow[2] = 0b01111111_11111111_11100000_00001111_11111111_11111100_00000001_11111111L;
            midCurlStateHigh[2] = 0b11111111_11000000_00011111_11111111_11111000_00000011_11111111_11111111L;
            midCurlStateLow[3] = 0b11111111_11000000_00000000_00000000_00000111_11111111_11111111_11111111L;
            midCurlStateHigh[3] = 0b00000000_00111111_11111111_11111111_11111111_11111111_11111111_11111111L;
        }

        if (numberOfThreads <= 0) {
            numberOfThreads = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        }

        Thread[] workers = new Thread[numberOfThreads];

        while (numberOfThreads-- > 0) {

            final int threadIndex = numberOfThreads;
            Thread worker = (new Thread(() -> {

                final long[] midCurlStateCopyLow = new long[CURL_STATE_LENGTH], midCurlStateCopyHigh = new long[CURL_STATE_LENGTH];
                System.arraycopy(midCurlStateLow, 0, midCurlStateCopyLow, 0, CURL_STATE_LENGTH);
                System.arraycopy(midCurlStateHigh, 0, midCurlStateCopyHigh, 0, CURL_STATE_LENGTH);
                for (int i = threadIndex; i-- > 0; ) {
                    increment(midCurlStateCopyLow, midCurlStateCopyHigh, CURL_HASH_LENGTH / 3,
                        (CURL_HASH_LENGTH / 3) * 2);
                }

                final long[] curlStateLow = new long[CURL_STATE_LENGTH], curlStateHigh = new long[CURL_STATE_LENGTH];
                final long[] curlScratchpadLow = new long[CURL_STATE_LENGTH], curlScratchpadHigh = new long[CURL_STATE_LENGTH];
                long mask, outMask = 1;
                while (state == RUNNING) {

                    increment(midCurlStateCopyLow, midCurlStateCopyHigh, (CURL_HASH_LENGTH / 3) * 2,
                        CURL_HASH_LENGTH);
                    System.arraycopy(midCurlStateCopyLow, 0, curlStateLow, 0, CURL_STATE_LENGTH);
                    System.arraycopy(midCurlStateCopyHigh, 0, curlStateHigh, 0, CURL_STATE_LENGTH);
                    transform(curlStateLow, curlStateHigh, curlScratchpadLow, curlScratchpadHigh);

                    mask = HIGH_BITS;
                    for (int i = minWeightMagnitude; i-- > 0; ) {
                        mask &= ~(curlStateLow[CURL_HASH_LENGTH - 1 - i] ^ curlStateHigh[
                            CURL_HASH_LENGTH - 1 - i]);
                        if (mask == 0) {
                            break;
                        }
                    }
                    if (mask == 0) {
                        continue;
                    }

                    synchronized (syncObj) {
                        if (state == RUNNING) {
                            state = COMPLETED;
                            while ((outMask & mask) == 0) {
                                outMask <<= 1;
                            }
                            for (int i = 0; i < CURL_HASH_LENGTH; i++) {
                                transactionTrits[TRANSACTION_LENGTH - CURL_HASH_LENGTH + i] =
                                    (midCurlStateCopyLow[i] & outMask) == 0 ? 1
                                        : (midCurlStateCopyHigh[i] & outMask) == 0 ? -1 : 0;
                            }
                            syncObj.notifyAll();
                        }
                    }
                    break;
                }
            }));
            workers[threadIndex] = worker;
            worker.start();
        }

        try {
            synchronized (syncObj) {
                if (state == RUNNING) {
                    syncObj.wait();
                }
            }
        } catch (final InterruptedException e) {
            synchronized (syncObj) {
                state = CANCELLED;
            }
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

    private static void transform(final long[] curlStateLow, final long[] curlStateHigh,
        final long[] curlScratchpadLow, final long[] curlScratchpadHigh) {

        int curlScratchpadIndex = 0;
        for (int round = 0; round < 27; round++) {
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
