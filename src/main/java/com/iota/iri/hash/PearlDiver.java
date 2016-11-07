package com.iota.iri.hash;

public class PearlDiver {

    private static final int TRANSACTION_LENGTH = 8019;

    private static final int HASH_LENGTH = 243;
    private static final int STATE_LENGTH = HASH_LENGTH * 3;

    private boolean finished, interrupted;

    public synchronized void interrupt() {

        finished = true;
        interrupted = true;

        notifyAll();
    }

    public synchronized boolean search(final int[] transactionTrits, final int minWeightMagnitude, int numberOfThreads) {

        if (transactionTrits.length != TRANSACTION_LENGTH) {
            throw new RuntimeException("Invalid transaction trits length: " + transactionTrits.length);
        }
        if (minWeightMagnitude < 0 || minWeightMagnitude > HASH_LENGTH) {
            throw new RuntimeException("Invalid min weight magnitude: " + minWeightMagnitude);
        }

        finished = false;
        interrupted = false;

        final long[] midStateLow = new long[STATE_LENGTH], midStateHigh = new long[STATE_LENGTH];

        {
            for (int i = HASH_LENGTH; i < STATE_LENGTH; i++) {

                midStateLow[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                midStateHigh[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
            }

            int offset = 0;
            final long[] scratchpadLow = new long[STATE_LENGTH], scratchpadHigh = new long[STATE_LENGTH];
            for (int i = (TRANSACTION_LENGTH - HASH_LENGTH) / HASH_LENGTH; i-- > 0; ) {

                for (int j = 0; j < HASH_LENGTH; j++) {

                    switch (transactionTrits[offset++]) {

                        case 0: {

                            midStateLow[j] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                            midStateHigh[j] = 0b1111111111111111111111111111111111111111111111111111111111111111L;

                        } break;

                        case 1: {

                            midStateLow[j] = 0b0000000000000000000000000000000000000000000000000000000000000000L;
                            midStateHigh[j] = 0b1111111111111111111111111111111111111111111111111111111111111111L;

                        } break;

                        default: {

                            midStateLow[j] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                            midStateHigh[j] = 0b0000000000000000000000000000000000000000000000000000000000000000L;
                        }
                    }
                }

                transform(midStateLow, midStateHigh, scratchpadLow, scratchpadHigh);
            }

            midStateLow[0] = 0b1101101101101101101101101101101101101101101101101101101101101101L;
            midStateHigh[0] = 0b1011011011011011011011011011011011011011011011011011011011011011L;
            midStateLow[1] = 0b1111000111111000111111000111111000111111000111111000111111000111L;
            midStateHigh[1] = 0b1000111111000111111000111111000111111000111111000111111000111111L;
            midStateLow[2] = 0b0111111111111111111000000000111111111111111111000000000111111111L;
            midStateHigh[2] = 0b1111111111000000000111111111111111111000000000111111111111111111L;
            midStateLow[3] = 0b1111111111000000000000000000000000000111111111111111111111111111L;
            midStateHigh[3] = 0b0000000000111111111111111111111111111111111111111111111111111111L;
        }

        if (numberOfThreads <= 0) {

            numberOfThreads = Runtime.getRuntime().availableProcessors() - 1;
            if (numberOfThreads < 1) {

                numberOfThreads = 1;
            }
        }

        while (numberOfThreads-- > 0) {

            final int threadIndex = numberOfThreads;
            (new Thread(() -> {

                final long[] midStateCopyLow = new long[STATE_LENGTH], midStateCopyHigh = new long[STATE_LENGTH];
                System.arraycopy(midStateLow, 0, midStateCopyLow, 0, STATE_LENGTH);
                System.arraycopy(midStateHigh, 0, midStateCopyHigh, 0, STATE_LENGTH);
                for (int i = threadIndex; i-- > 0; ) {

                    increment(midStateCopyLow, midStateCopyHigh, HASH_LENGTH / 3, (HASH_LENGTH / 3) * 2);
                }

                final long[] stateLow = new long[STATE_LENGTH], stateHigh = new long[STATE_LENGTH];
                final long[] scratchpadLow = new long[STATE_LENGTH], scratchpadHigh = new long[STATE_LENGTH];
                while (!finished) {

                    increment(midStateCopyLow, midStateCopyHigh, (HASH_LENGTH / 3) * 2, HASH_LENGTH);
                    System.arraycopy(midStateCopyLow, 0, stateLow, 0, STATE_LENGTH);
                    System.arraycopy(midStateCopyHigh, 0, stateHigh, 0, STATE_LENGTH);
                    transform(stateLow, stateHigh, scratchpadLow, scratchpadHigh);

                NEXT_BIT_INDEX:
                    for (int bitIndex = 64; bitIndex-- > 0; ) {

                        for (int i = minWeightMagnitude; i-- > 0; ) {

                            if ((((int)(stateLow[HASH_LENGTH - 1 - i] >> bitIndex)) & 1) != (((int)(stateHigh[HASH_LENGTH - 1 - i] >> bitIndex)) & 1)) {

                                continue NEXT_BIT_INDEX;
                            }
                        }

                        finished = true;

                        synchronized (this) {

                            for (int i = 0; i < HASH_LENGTH; i++) {

                                transactionTrits[TRANSACTION_LENGTH - HASH_LENGTH + i] = ((((int)(midStateCopyLow[i] >> bitIndex)) & 1) == 0) ? 1 : (((((int)(midStateCopyHigh[i] >> bitIndex)) & 1) == 0) ? -1 : 0);
                            }

                            notifyAll();
                        }

                        break;
                    }
                }

            })).start();
        }

        try {

            wait();

        } catch (final InterruptedException e) {
            // ignore
        }

        return interrupted;
    }

    private static void transform(final long[] stateLow, final long[] stateHigh, final long[] scratchpadLow, final long[] scratchpadHigh) {

        int scratchpadIndex = 0;
        for (int round = 27; round-- > 0; ) {

            System.arraycopy(stateLow, 0, scratchpadLow, 0, STATE_LENGTH);
            System.arraycopy(stateHigh, 0, scratchpadHigh, 0, STATE_LENGTH);

            for (int stateIndex = 0; stateIndex < STATE_LENGTH; stateIndex++) {

                final long alpha = scratchpadLow[scratchpadIndex];
                final long beta = scratchpadHigh[scratchpadIndex];
                final long gamma = scratchpadHigh[scratchpadIndex += (scratchpadIndex < 365 ? 364 : -365)];
                final long delta = (alpha | (~gamma)) & (scratchpadLow[scratchpadIndex] ^ beta);

                stateLow[stateIndex] = ~delta;
                stateHigh[stateIndex] = (alpha ^ gamma) | delta;
            }
        }
    }

    private static void increment(final long[] midStateCopyLow, final long[] midStateCopyHigh, final int fromIndex, final int toIndex) {

        for (int i = fromIndex; i < toIndex; i++) {

            if (midStateCopyLow[i] == 0b0000000000000000000000000000000000000000000000000000000000000000L) {
                midStateCopyLow[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                midStateCopyHigh[i] = 0b0000000000000000000000000000000000000000000000000000000000000000L;
            } else {

                if (midStateCopyHigh[i] == 0b0000000000000000000000000000000000000000000000000000000000000000L) {
                    midStateCopyHigh[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                } else {
                    midStateCopyLow[i] = 0b0000000000000000000000000000000000000000000000000000000000000000L;
                }
                break;
            }
        }
    }
}