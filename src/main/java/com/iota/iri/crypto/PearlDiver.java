package com.iota.iri.crypto;

import java.util.ArrayList;
import java.util.List;

/**
 * Proof of Work calculator.
 * <p>
 *     Given a transaction's trits, computes an additional nonce such that
 *     the hash ends with {@code minWeightMagnitude} zeros:<br>
 * </p>
 * <pre>
 *     trailingZeros(hash(transaction || nonce)) < minWeightMagnitude
 * </pre>
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

    /**
     * Searches for a nonce such that the hash ends with {@code minWeightMagnitude} zeros.<br>
     * To add the {@value com.iota.iri.controllers.TransactionViewModel#NONCE_TRINARY_SIZE}
     * trits long nounce {@code transactionTrits} are changed from the following offset:
     * {@value com.iota.iri.controllers.TransactionViewModel#NONCE_TRINARY_OFFSET} <br>
     *
     * @param transactionTrits trits of transaction
     * @param minWeightMagnitude target weight for trailing zeros
     * @param numberOfThreads number of worker threads to search for a nonce
     * @return <tt>true</tt> if search completed successfully.
     * the nonce will be written to the end of {@code transactionTrits}
     */
    public synchronized boolean search(final byte[] transactionTrits, final int minWeightMagnitude,
                                       int numberOfThreads) {

        validateParameters(transactionTrits, minWeightMagnitude);
        synchronized (syncObj) {
            state = State.RUNNING;
        }

        final long[] midStateLow = new long[CURL_STATE_LENGTH];
        final long[] midStateHigh = new long[CURL_STATE_LENGTH];
        initializeMidCurlStates(transactionTrits, midStateLow, midStateHigh);

        if (numberOfThreads <= 0) {
            int available = Runtime.getRuntime().availableProcessors();
            numberOfThreads = Math.max(1, Math.floorDiv(available * 8, 10));
        }
        List<Thread> workers = new ArrayList<>(numberOfThreads);
        while (numberOfThreads-- > 0) {
            long[] midStateCopyLow = midStateLow.clone();
            long[] midStateCopyHigh = midStateHigh.clone();
            Runnable runnable = getRunnable(numberOfThreads, transactionTrits, minWeightMagnitude, midStateCopyLow, midStateCopyHigh);
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
                    state = State.CANCELLED;
                }
            }
        }
        return state == State.COMPLETED;
    }

    /**
     * Cancels the running search task.
     */
    public void cancel() {
        synchronized (syncObj) {
            state = State.CANCELLED;
        }
    }

    private static void validateParameters(byte[] transactionTrits, int minWeightMagnitude) {
        if (transactionTrits.length != TRANSACTION_LENGTH) {
            throw new RuntimeException(
                    "Invalid transaction trits length: " + transactionTrits.length);
        }
        if (minWeightMagnitude < 0 || minWeightMagnitude > CURL_HASH_LENGTH) {
            throw new RuntimeException("Invalid min weight magnitude: " + minWeightMagnitude);
        }
    }

    private Runnable getRunnable(final int threadIndex, final byte[] transactionTrits, final int minWeightMagnitude,
                                 final long[] midStateCopyLow, final long[] midStateCopyHigh) {
        return () -> {
            for (int i = 0; i < threadIndex; i++) {
                increment(midStateCopyLow, midStateCopyHigh, 162 + CURL_HASH_LENGTH / 9,
                    162 + (CURL_HASH_LENGTH / 9) * 2);
            }

            final long[] stateLow = new long[CURL_STATE_LENGTH];
            final long[] stateHigh = new long[CURL_STATE_LENGTH];

            final long[] scratchpadLow = new long[CURL_STATE_LENGTH];
            final long[] scratchpadHigh = new long[CURL_STATE_LENGTH];

            final int maskStartIndex = CURL_HASH_LENGTH - minWeightMagnitude;
            long mask = 0;
            while (state == State.RUNNING && mask == 0) {

                increment(midStateCopyLow, midStateCopyHigh, 162 + (CURL_HASH_LENGTH / 9) * 2,
                    CURL_HASH_LENGTH);

                copy(midStateCopyLow, midStateCopyHigh, stateLow, stateHigh);
                transform(stateLow, stateHigh, scratchpadLow, scratchpadHigh);

                mask = HIGH_BITS;
                for (int i = maskStartIndex; i < CURL_HASH_LENGTH && mask != 0; i++) {
                    mask &= ~(stateLow[i] ^ stateHigh[i]);
                }
            }
            if (mask != 0) {
                synchronized (syncObj) {
                    if (state == State.RUNNING) {
                        state = State.COMPLETED;
                        long outMask = 1;
                        while ((outMask & mask) == 0) {
                            outMask <<= 1;
                        }
                        for (int i = 0; i < CURL_HASH_LENGTH; i++) {
                            transactionTrits[TRANSACTION_LENGTH - CURL_HASH_LENGTH + i] =
                                (midStateCopyLow[i] & outMask) == 0 ? 1
                                    : (midStateCopyHigh[i] & outMask) == 0 ? (byte) -1 : (byte) 0;
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

    private static void initializeMidCurlStates(byte[] transactionTrits, long[] midStateLow, long[] midStateHigh) {
        for (int i = CURL_HASH_LENGTH; i < CURL_STATE_LENGTH; i++) {
            midStateLow[i] = HIGH_BITS;
            midStateHigh[i] = HIGH_BITS;
        }

        int offset = 0;
        final long[] curlScratchpadLow = new long[CURL_STATE_LENGTH];
        final long[] curlScratchpadHigh = new long[CURL_STATE_LENGTH];
        for (int i = (TRANSACTION_LENGTH - CURL_HASH_LENGTH) / CURL_HASH_LENGTH; i-- > 0; ) {

            for (int j = 0; j < CURL_HASH_LENGTH; j++) {
                switch (transactionTrits[offset++]) {
                    case 0:
                        midStateLow[j] = HIGH_BITS;
                        midStateHigh[j] = HIGH_BITS;
                        break;
                    case 1:
                        midStateLow[j] = LOW_BITS;
                        midStateHigh[j] = HIGH_BITS;
                        break;
                    default:
                        midStateLow[j] = HIGH_BITS;
                        midStateHigh[j] = LOW_BITS;
                }
            }
            transform(midStateLow, midStateHigh, curlScratchpadLow, curlScratchpadHigh);
        }

        for (int i = 0; i < 162; i++) {
            switch (transactionTrits[offset++]) {
                case 0:
                    midStateLow[i] = HIGH_BITS;
                    midStateHigh[i] = HIGH_BITS;
                    break;
                case 1:
                    midStateLow[i] = LOW_BITS;
                    midStateHigh[i] = HIGH_BITS;
                    break;
                default:
                    midStateLow[i] = HIGH_BITS;
                    midStateHigh[i] = LOW_BITS;
            }
        }

        midStateLow[162 + 0] = 0b1101101101101101101101101101101101101101101101101101101101101101L;
        midStateHigh[162 + 0] = 0b1011011011011011011011011011011011011011011011011011011011011011L;
        midStateLow[162 + 1] = 0b1111000111111000111111000111111000111111000111111000111111000111L;
        midStateHigh[162 + 1] = 0b1000111111000111111000111111000111111000111111000111111000111111L;
        midStateLow[162 + 2] = 0b0111111111111111111000000000111111111111111111000000000111111111L;
        midStateHigh[162 + 2] = 0b1111111111000000000111111111111111111000000000111111111111111111L;
        midStateLow[162 + 3] = 0b1111111111000000000000000000000000000111111111111111111111111111L;
        midStateHigh[162 + 3] = 0b0000000000111111111111111111111111111111111111111111111111111111L;
    }

    private static void transform(final long[] stateLow, final long[] stateHigh,
                                  final long[] scratchpadLow, final long[] scratchpadHigh) {

        for (int round = 0; round < Curl.NUMBER_OF_ROUNDSP81; round++) {
            copy(stateLow, stateHigh, scratchpadLow, scratchpadHigh);

            int scratchpadIndex = 0;
            for (int stateIndex = 0; stateIndex < CURL_STATE_LENGTH; stateIndex++) {
                final long alpha = scratchpadLow[scratchpadIndex];
                final long beta = scratchpadHigh[scratchpadIndex];
                if (scratchpadIndex < 365) {
                    scratchpadIndex += 364;
                } else {
                    scratchpadIndex += -365;
                }
                final long gamma = scratchpadHigh[scratchpadIndex];
                final long delta = (alpha | (~gamma)) & (scratchpadLow[scratchpadIndex] ^ beta);

                stateLow[stateIndex] = ~delta;
                stateHigh[stateIndex] = (alpha ^ gamma) | delta;
            }
        }
    }

    private static void increment(final long[] midStateCopyLow, final long[] midStateCopyHigh,
                                  final int fromIndex, final int toIndex) {

        for (int i = fromIndex; i < toIndex; i++) {
            if (midStateCopyLow[i] == LOW_BITS) {
                midStateCopyLow[i] = HIGH_BITS;
                midStateCopyHigh[i] = LOW_BITS;
            } else if (midStateCopyHigh[i] == LOW_BITS) {
                midStateCopyHigh[i] = HIGH_BITS;
                break;
            } else {
                midStateCopyLow[i] = LOW_BITS;
                break;
            }
        }
    }
}