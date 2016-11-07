package com.iota.iri.service;

import com.iota.iri.Milestone;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Storage {

    public static final int CELL_SIZE = 2048;
    private static final int CELLS_PER_CHUNK = 65536;
    private static final int CHUNK_SIZE = CELL_SIZE * CELLS_PER_CHUNK;
    private static final int MAX_NUMBER_OF_CHUNKS = 16384; // Limits the storage capacity to ~1 billion transactions

    private static final int TIPS_FLAGS_OFFSET = 0, TIPS_FLAGS_SIZE = MAX_NUMBER_OF_CHUNKS * CELLS_PER_CHUNK / Byte.SIZE;
    public static final int SUPER_GROUPS_OFFSET = TIPS_FLAGS_OFFSET + TIPS_FLAGS_SIZE, SUPER_GROUPS_SIZE = (Short.MAX_VALUE - Short.MIN_VALUE + 1) * CELL_SIZE;
    public static final int CELLS_OFFSET = SUPER_GROUPS_OFFSET + SUPER_GROUPS_SIZE;

    private static final int TRANSACTIONS_TO_REQUEST_OFFSET = 0, TRANSACTIONS_TO_REQUEST_SIZE = CHUNK_SIZE;
    private static final int ANALYZED_TRANSACTIONS_FLAGS_OFFSET = TRANSACTIONS_TO_REQUEST_OFFSET + TRANSACTIONS_TO_REQUEST_SIZE, ANALYZED_TRANSACTIONS_FLAGS_SIZE = MAX_NUMBER_OF_CHUNKS * CELLS_PER_CHUNK / Byte.SIZE;
    private static final int ANALYZED_TRANSACTIONS_FLAGS_COPY_OFFSET = ANALYZED_TRANSACTIONS_FLAGS_OFFSET + ANALYZED_TRANSACTIONS_FLAGS_SIZE, ANALYZED_TRANSACTIONS_FLAGS_COPY_SIZE = ANALYZED_TRANSACTIONS_FLAGS_SIZE;

    private static final int GROUP = 0;
    public static final int PREFILLED_SLOT = 1;
    public static final int FILLED_SLOT = -1;

    public static final byte[] ZEROED_BUFFER = new byte[CELL_SIZE];

    private static final String TRANSACTIONS_FILE_NAME = "transactions.iri";
    private static final String BUNDLES_FILE_NAME = "bundles.iri";
    private static final String ADDRESSES_FILE_NAME = "addresses.iri";
    private static final String TAGS_FILE_NAME = "tags.iri";
    private static final String APPROVERS_FILE_NAME = "approvers.iri";
    private static final String SCRATCHPAD_FILE_NAME = "scratchpad.iri";

    private static final int ZEROTH_POINTER_OFFSET = 64;

    private static FileChannel transactionsChannel;
    public static ByteBuffer transactionsTipsFlags;
    private static final ByteBuffer[] transactionsChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
    private volatile static long transactionsNextPointer = CELLS_OFFSET - SUPER_GROUPS_OFFSET;

    private static final byte[] mainBuffer = new byte[CELL_SIZE], auxBuffer = new byte[CELL_SIZE];
    public static final byte[][] approvedTransactionsToStore = new byte[2][];
    
    public static int numberOfApprovedTransactionsToStore;

    private static FileChannel bundlesChannel;
    private static final ByteBuffer[] bundlesChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
    private volatile static long bundlesNextPointer = SUPER_GROUPS_SIZE;

    private static FileChannel addressesChannel;
    private static final ByteBuffer[] addressesChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
    private volatile static long addressesNextPointer = SUPER_GROUPS_SIZE;

    private static FileChannel tagsChannel;
    private  static final ByteBuffer[] tagsChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
    private volatile static long tagsNextPointer = SUPER_GROUPS_SIZE;

    private static FileChannel approversChannel;
    private static final ByteBuffer[] approversChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
    private volatile static long approversNextPointer = SUPER_GROUPS_SIZE;

    private static ByteBuffer transactionsToRequest;
    public volatile static int numberOfTransactionsToRequest;
    private static final byte[] transactionToRequest = new byte[Transaction.HASH_SIZE];

    public static ByteBuffer analyzedTransactionsFlags, analyzedTransactionsFlagsCopy;

    private static boolean launched;

    private static final Object transactionToRequestMonitor = new Object();
    private static int previousNumberOfTransactions;

    public static synchronized void launch() throws IOException {

        transactionsChannel = FileChannel.open(Paths.get(TRANSACTIONS_FILE_NAME), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        transactionsTipsFlags = transactionsChannel.map(FileChannel.MapMode.READ_WRITE, TIPS_FLAGS_OFFSET, TIPS_FLAGS_SIZE);
        transactionsChunks[0] = transactionsChannel.map(FileChannel.MapMode.READ_WRITE, SUPER_GROUPS_OFFSET, SUPER_GROUPS_SIZE);
        final long transactionsChannelSize = transactionsChannel.size();
        while (true) {

            if ((transactionsNextPointer & (CHUNK_SIZE - 1)) == 0) {

                transactionsChunks[(int)(transactionsNextPointer >> 27)] = transactionsChannel.map(FileChannel.MapMode.READ_WRITE, SUPER_GROUPS_OFFSET + transactionsNextPointer, CHUNK_SIZE);
            }

            if (transactionsChannelSize - transactionsNextPointer - SUPER_GROUPS_OFFSET > CHUNK_SIZE) {

                transactionsNextPointer += CHUNK_SIZE;

            } else {

                transactionsChunks[(int) (transactionsNextPointer >> 27)].get(mainBuffer);
                boolean empty = true;
                for (final int value : mainBuffer) {

                    if (value != 0) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    break;
                }

                transactionsNextPointer += CELL_SIZE;
            }
        }

        bundlesChannel = FileChannel.open(Paths.get(BUNDLES_FILE_NAME), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        bundlesChunks[0] = bundlesChannel.map(FileChannel.MapMode.READ_WRITE, 0, SUPER_GROUPS_SIZE);
        final long bundlesChannelSize = bundlesChannel.size();
        while (true) {

            if ((bundlesNextPointer & (CHUNK_SIZE - 1)) == 0) {

                bundlesChunks[(int)(bundlesNextPointer >> 27)] = bundlesChannel.map(FileChannel.MapMode.READ_WRITE, bundlesNextPointer, CHUNK_SIZE);
            }

            if (bundlesChannelSize - bundlesNextPointer > CHUNK_SIZE) {

                bundlesNextPointer += CHUNK_SIZE;

            } else {

                bundlesChunks[(int) (bundlesNextPointer >> 27)].get(mainBuffer);
                boolean empty = true;
                for (final int value : mainBuffer) {

                    if (value != 0) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    break;
                }

                bundlesNextPointer += CELL_SIZE;
            }
        }

        addressesChannel = FileChannel.open(Paths.get(ADDRESSES_FILE_NAME), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        addressesChunks[0] = addressesChannel.map(FileChannel.MapMode.READ_WRITE, 0, SUPER_GROUPS_SIZE);
        final long addressesChannelSize = addressesChannel.size();
        while (true) {

            if ((addressesNextPointer & (CHUNK_SIZE - 1)) == 0) {

                addressesChunks[(int)(addressesNextPointer >> 27)] = addressesChannel.map(FileChannel.MapMode.READ_WRITE, addressesNextPointer, CHUNK_SIZE);
            }

            if (addressesChannelSize - addressesNextPointer > CHUNK_SIZE) {

                addressesNextPointer += CHUNK_SIZE;

            } else {

                addressesChunks[(int) (addressesNextPointer >> 27)].get(mainBuffer);
                boolean empty = true;
                for (final int value : mainBuffer) {

                    if (value != 0) {

                        empty = false;

                        break;
                    }
                }
                if (empty) {

                    break;
                }

                addressesNextPointer += CELL_SIZE;
            }
        }

        tagsChannel = FileChannel.open(Paths.get(TAGS_FILE_NAME), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        tagsChunks[0] = tagsChannel.map(FileChannel.MapMode.READ_WRITE, 0, SUPER_GROUPS_SIZE);
        final long tagsChannelSize = tagsChannel.size();
        while (true) {

            if ((tagsNextPointer & (CHUNK_SIZE - 1)) == 0) {

                tagsChunks[(int)(tagsNextPointer >> 27)] = tagsChannel.map(FileChannel.MapMode.READ_WRITE, tagsNextPointer, CHUNK_SIZE);
            }

            if (tagsChannelSize - tagsNextPointer > CHUNK_SIZE) {

                tagsNextPointer += CHUNK_SIZE;

            } else {

                tagsChunks[(int) (tagsNextPointer >> 27)].get(mainBuffer);
                boolean empty = true;
                for (final int value : mainBuffer) {

                    if (value != 0) {

                        empty = false;

                        break;
                    }
                }
                if (empty) {

                    break;
                }

                tagsNextPointer += CELL_SIZE;
            }
        }

        approversChannel = FileChannel.open(Paths.get(APPROVERS_FILE_NAME), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        approversChunks[0] = approversChannel.map(FileChannel.MapMode.READ_WRITE, 0, SUPER_GROUPS_SIZE);
        final long approversChannelSize = approversChannel.size();
        while (true) {

            if ((approversNextPointer & (CHUNK_SIZE - 1)) == 0) {

                approversChunks[(int)(approversNextPointer >> 27)] = approversChannel.map(FileChannel.MapMode.READ_WRITE, approversNextPointer, CHUNK_SIZE);
            }

            if (approversChannelSize - approversNextPointer > CHUNK_SIZE) {

                approversNextPointer += CHUNK_SIZE;

            } else {

                approversChunks[(int) (approversNextPointer >> 27)].get(mainBuffer);
                boolean empty = true;
                for (final int value : mainBuffer) {

                    if (value != 0) {

                        empty = false;

                        break;
                    }
                }
                if (empty) {

                    break;
                }

                approversNextPointer += CELL_SIZE;
            }
        }

        final FileChannel scratchpadChannel = FileChannel.open(Paths.get(SCRATCHPAD_FILE_NAME), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        transactionsToRequest = scratchpadChannel.map(FileChannel.MapMode.READ_WRITE, TRANSACTIONS_TO_REQUEST_OFFSET, TRANSACTIONS_TO_REQUEST_SIZE);
        analyzedTransactionsFlags = scratchpadChannel.map(FileChannel.MapMode.READ_WRITE, ANALYZED_TRANSACTIONS_FLAGS_OFFSET, ANALYZED_TRANSACTIONS_FLAGS_SIZE);
        analyzedTransactionsFlagsCopy = scratchpadChannel.map(FileChannel.MapMode.READ_WRITE, ANALYZED_TRANSACTIONS_FLAGS_COPY_OFFSET, ANALYZED_TRANSACTIONS_FLAGS_COPY_SIZE);
        scratchpadChannel.close();

        if (transactionsNextPointer == CELLS_OFFSET - SUPER_GROUPS_OFFSET) {

            // No need to zero "mainBuffer", it already contains only zeros
            setValue(mainBuffer, Transaction.TYPE_OFFSET, FILLED_SLOT);
            appendToTransactions(true);

            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
            setValue(mainBuffer, 128 << 3, CELLS_OFFSET - SUPER_GROUPS_OFFSET);
            ((ByteBuffer)transactionsChunks[0].position((128 + (128 << 8)) << 11)).put(mainBuffer);

            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
            updateBundleAddressTagAndApprovers(CELLS_OFFSET - SUPER_GROUPS_OFFSET);
        }

        launched = true;
    }

    public static synchronized void shutDown() {

        if (launched) {

            ((MappedByteBuffer) transactionsTipsFlags).force();
            for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && transactionsChunks[i] != null; i++) {
                System.out.println("Flushing transactions chunk #" + i);
                flush(transactionsChunks[i]);
            }

            for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && bundlesChunks[i] != null; i++) {
                System.out.println("Flushing bundles chunk #" + i);
                flush(bundlesChunks[i]);
            }

            for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && addressesChunks[i] != null; i++) {
                System.out.println("Flushing addresses chunk #" + i);
                flush(addressesChunks[i]);
            }

            for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && tagsChunks[i] != null; i++) {
                System.out.println("Flushing tags chunk #" + i);
                flush(tagsChunks[i]);
            }

            for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && approversChunks[i] != null; i++) {
                System.out.println("Flushing approvers chunk #" + i);
                flush(approversChunks[i]);
            }

            System.out.println("DB successfully flushed");

            try {

                transactionsChannel.close();
                bundlesChannel.close();
                addressesChannel.close();
                tagsChannel.close();
                approversChannel.close();

            } catch (final Exception e) {
            }
        }
    }

    private static boolean flush(final ByteBuffer buffer) {

        try {
            ((MappedByteBuffer) buffer).force();
            return true;

        } catch (final Exception e) {
            return false;
        }
    }

    public static synchronized long storeTransaction(final byte[] hash, final Transaction transaction, final boolean tip) { // Returns the pointer or 0 if the transaction was already in the storage and "transaction" value is not null

        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11, prevPointer = 0;

    MAIN_LOOP:
        for (int depth = 2; depth < Transaction.HASH_SIZE; depth++) {

            ((ByteBuffer)transactionsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                prevPointer = pointer;
                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {

                    setValue(mainBuffer, (hash[depth] + 128) << 3, pointer = transactionsNextPointer);
                    ((ByteBuffer)transactionsChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                    Transaction.dump(mainBuffer, hash, transaction);
                    appendToTransactions(transaction != null || tip);
                    if (transaction != null) {

                        updateBundleAddressTagAndApprovers(pointer);
                    }

                    break MAIN_LOOP;
                }

            } else {

                for (int i = depth; i < Transaction.HASH_SIZE; i++) {

                    if (mainBuffer[Transaction.HASH_OFFSET + i] != hash[i]) {

                        final int differentHashByte = mainBuffer[Transaction.HASH_OFFSET + i];

                        ((ByteBuffer)transactionsChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        setValue(mainBuffer, (hash[depth - 1] + 128) << 3, transactionsNextPointer);
                        ((ByteBuffer)transactionsChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                        for (int j = depth; j < i; j++) {

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            setValue(mainBuffer, (hash[j] + 128) << 3, transactionsNextPointer + CELL_SIZE);
                            appendToTransactions(false);
                        }

                        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                        setValue(mainBuffer, (differentHashByte + 128) << 3, pointer);
                        setValue(mainBuffer, (hash[i] + 128) << 3, transactionsNextPointer + CELL_SIZE);
                        appendToTransactions(false);

                        Transaction.dump(mainBuffer, hash, transaction);
                        pointer = transactionsNextPointer;
                        appendToTransactions(transaction != null || tip);
                        if (transaction != null) {

                            updateBundleAddressTagAndApprovers(pointer);
                        }

                        break MAIN_LOOP;
                    }
                }

                if (transaction != null) {

                    if (mainBuffer[Transaction.TYPE_OFFSET] == PREFILLED_SLOT) {

                        Transaction.dump(mainBuffer, hash, transaction);
                        ((ByteBuffer)transactionsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
                        updateBundleAddressTagAndApprovers(pointer);

                    } else {

                        pointer = 0;
                    }
                }

                break MAIN_LOOP;
            }
        }

        return pointer;
    }

    public static synchronized long transactionPointer(final byte[] hash) { // Returns a negative value if the transaction hasn't been seen yet but was referenced

        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11;
        for (int depth = 2; depth < Transaction.HASH_SIZE; depth++) {

            ((ByteBuffer)transactionsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(auxBuffer);

            if (auxBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                if ((pointer = value(auxBuffer, (hash[depth] + 128) << 3)) == 0) {

                    return 0;
                }

            } else {

                for (; depth < Transaction.HASH_SIZE; depth++) {

                    if (auxBuffer[Transaction.HASH_OFFSET + depth] != hash[depth]) {

                        return 0;
                    }
                }

                return auxBuffer[Transaction.TYPE_OFFSET] == PREFILLED_SLOT ? -pointer : pointer;
            }
        }

        throw new IllegalStateException("Corrupted storage");
    }

    public static synchronized Transaction loadTransaction(final long pointer) {

        ((ByteBuffer)transactionsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
        return new Transaction(mainBuffer, pointer);
    }

    public static synchronized Transaction loadTransaction(final byte[] hash) {

        final long pointer = transactionPointer(hash);
        return pointer > 0 ? loadTransaction(pointer) : null;
    }

    public static void transactionToRequest(final byte[] buffer, final int offset) {

        synchronized (transactionToRequestMonitor) {

            if (numberOfTransactionsToRequest == 0) {

                final long beginningTime = System.currentTimeMillis();

                synchronized (analyzedTransactionsFlags) {

                    clearAnalyzedTransactionsFlags();

                    final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(transactionPointer(Milestone.latestMilestone.bytes())));
                    Long pointer;
                    while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                        if (Storage.setAnalyzedTransactionFlag(pointer)) {

                            final Transaction transaction = Storage.loadTransaction(pointer);
                            if (transaction.type == Storage.PREFILLED_SLOT) {

                                ((ByteBuffer) transactionsToRequest.position(numberOfTransactionsToRequest++ * Transaction.HASH_SIZE)).put(transaction.hash); // Only 2'917'776 hashes can be stored this way without overflowing the buffer, we assume that nodes will never need to store that many hashes, so we don't need to cap "numberOfTransactionsToRequest"

                            } else {

                                nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                                nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);
                            }
                        }
                    }
                }

                System.out.println("Transactions to request = " + numberOfTransactionsToRequest + " / " + (transactionsNextPointer - (CELLS_OFFSET - SUPER_GROUPS_OFFSET)) / CELL_SIZE + " (" + (System.currentTimeMillis() - beginningTime) + " ms / " + (numberOfTransactionsToRequest == 0 ? 0 : (previousNumberOfTransactions == 0 ? 0 : (((transactionsNextPointer - (CELLS_OFFSET - SUPER_GROUPS_OFFSET)) / CELL_SIZE - previousNumberOfTransactions) * 100) / numberOfTransactionsToRequest)) + "%)");
                previousNumberOfTransactions = (int) ((transactionsNextPointer - (CELLS_OFFSET - SUPER_GROUPS_OFFSET)) / CELL_SIZE);
            }

            if (numberOfTransactionsToRequest == 0) {
                System.arraycopy(Hash.NULL_HASH.bytes(), 0, buffer, offset, Transaction.HASH_SIZE);
            } else {

                ((ByteBuffer) transactionsToRequest.position(--numberOfTransactionsToRequest * Transaction.HASH_SIZE)).get(transactionToRequest);
                System.arraycopy(transactionToRequest, 0, buffer, offset, Transaction.HASH_SIZE);
            }
        }
    }

    public static synchronized boolean tipFlag(final long pointer) {
        final long index = (pointer - (CELLS_OFFSET - SUPER_GROUPS_OFFSET)) >> 11;
        return (transactionsTipsFlags.get((int)(index >> 3)) & (1 << (index & 7))) != 0;
    }

    public static synchronized List<Hash> tips() {

        final List<Hash> tips = new LinkedList<>();

        long pointer = CELLS_OFFSET - SUPER_GROUPS_OFFSET;
        while (pointer < transactionsNextPointer) {

            if (tipFlag(pointer)) {
                tips.add(new Hash(loadTransaction(pointer).hash, 0, Transaction.HASH_SIZE));
            }

            pointer += CELL_SIZE;
        }

        return tips;
    }

    public static void clearAnalyzedTransactionsFlags() {

        analyzedTransactionsFlags.position(0);
        for (int i = 0; i < ANALYZED_TRANSACTIONS_FLAGS_SIZE / CELL_SIZE; i++) {

            analyzedTransactionsFlags.put(ZEROED_BUFFER);
        }
    }

    public static boolean analyzedTransactionFlag(long pointer) {

        pointer -= CELLS_OFFSET - SUPER_GROUPS_OFFSET;

        return (analyzedTransactionsFlags.get((int) (pointer >> (11 + 3))) & (1 << ((pointer >> 11) & 7))) != 0;
    }

    public static boolean setAnalyzedTransactionFlag(long pointer) {

        pointer -= CELLS_OFFSET - SUPER_GROUPS_OFFSET;

        final int value = analyzedTransactionsFlags.get((int) (pointer >> (11 + 3)));
        if ((value & (1 << ((pointer >> 11) & 7))) == 0) {

            analyzedTransactionsFlags.put((int)(pointer >> (11 + 3)), (byte)(value | (1 << ((pointer >> 11) & 7))));

            return true;

        } else {

            return false;
        }
    }

    public static void saveAnalyzedTransactionsFlags() {

        analyzedTransactionsFlags.position(0);
        analyzedTransactionsFlagsCopy.position(0);
        analyzedTransactionsFlagsCopy.put(analyzedTransactionsFlags);
    }

    public static void loadAnalyzedTransactionsFlags() {

        analyzedTransactionsFlagsCopy.position(0);
        analyzedTransactionsFlags.position(0);
        analyzedTransactionsFlags.put(analyzedTransactionsFlagsCopy);
    }

    public static synchronized long bundlePointer(final byte[] hash) {

        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11;
        for (int depth = 2; depth < Transaction.BUNDLE_SIZE; depth++) {

            ((ByteBuffer)bundlesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {
                    return 0;
                }

            } else {

                for (; depth < Transaction.BUNDLE_SIZE; depth++) {

                    if (mainBuffer[Transaction.HASH_OFFSET + depth] != hash[depth]) {
                        return 0;
                    }
                }

                return pointer;
            }
        }

        throw new IllegalStateException("Corrupted storage");
    }

    public static synchronized List<Long> bundleTransactions(final long pointer) {

        final List<Long> bundleTransactions = new LinkedList<>();

        if (pointer != 0) {

            ((ByteBuffer) bundlesChunks[(int) (pointer >> 27)].position((int) (pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
            int offset = ZEROTH_POINTER_OFFSET - Long.BYTES;
            while (true) {

                while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES) {

                    final long transactionPointer = value(mainBuffer, offset);
                    if (transactionPointer == 0) {

                        break;

                    } else {

                        bundleTransactions.add(transactionPointer);
                    }
                }
                if (offset == CELL_SIZE - Long.BYTES) {

                    final long nextCellPointer = value(mainBuffer, offset);
                    if (nextCellPointer == 0) {

                        break;

                    } else {

                        ((ByteBuffer) bundlesChunks[(int) (nextCellPointer >> 27)].position((int) (nextCellPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        offset = -Long.BYTES;
                    }

                } else {

                    break;
                }
            }
        }

        return bundleTransactions;
    }

    public static synchronized long addressPointer(final byte[] hash) {

        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11;
        for (int depth = 2; depth < Transaction.ADDRESS_SIZE; depth++) {

            ((ByteBuffer)addressesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {

                    return 0;
                }

            } else {

                for (; depth < Transaction.ADDRESS_SIZE; depth++) {

                    if (mainBuffer[Transaction.HASH_OFFSET + depth] != hash[depth]) {

                        return 0;
                    }
                }

                return pointer;
            }
        }

        throw new IllegalStateException("Corrupted storage");
    }

    public static synchronized List<Long> addressTransactions(final long pointer) {

        final List<Long> addressTransactions = new LinkedList<>();

        if (pointer != 0) {

            ((ByteBuffer) addressesChunks[(int) (pointer >> 27)].position((int) (pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
            int offset = ZEROTH_POINTER_OFFSET - Long.BYTES;
            while (true) {

                while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES) {

                    final long transactionPointer = value(mainBuffer, offset);
                    if (transactionPointer == 0) {
                        break;
                    } else {
                        addressTransactions.add(transactionPointer);
                    }
                }
                if (offset == CELL_SIZE - Long.BYTES) {

                    final long nextCellPointer = value(mainBuffer, offset);
                    if (nextCellPointer == 0) {
                        break;
                    } else {
                        ((ByteBuffer) addressesChunks[(int) (nextCellPointer >> 27)].position((int) (nextCellPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        offset = -Long.BYTES;
                    }

                } else {
                    break;
                }
            }
        }

        return addressTransactions;
    }

    public static synchronized long tagPointer(final byte[] hash) {

        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11;
        for (int depth = 2; depth < Transaction.TAG_SIZE; depth++) {

            ((ByteBuffer) tagsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {
                    return 0;
                }

            } else {

                for (; depth < Transaction.TAG_SIZE; depth++) {
                    if (mainBuffer[Transaction.HASH_OFFSET + depth] != hash[depth]) {
                        return 0;
                    }
                }

                return pointer;
            }
        }

        throw new IllegalStateException("Corrupted storage");
    }

    public static synchronized List<Long> tagTransactions(final long pointer) {

        final List<Long> tagTransactions = new LinkedList<>();

        if (pointer != 0) {

            ((ByteBuffer) tagsChunks[(int) (pointer >> 27)].position((int) (pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
            int offset = ZEROTH_POINTER_OFFSET - Long.BYTES;
            while (true) {

                while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES) {

                    final long transactionPointer = value(mainBuffer, offset);
                    if (transactionPointer == 0) {

                        break;

                    } else {

                        tagTransactions.add(transactionPointer);
                    }
                }
                if (offset == CELL_SIZE - Long.BYTES) {

                    final long nextCellPointer = value(mainBuffer, offset);
                    if (nextCellPointer == 0) {

                        break;

                    } else {

                        ((ByteBuffer) tagsChunks[(int) (nextCellPointer >> 27)].position((int) (nextCellPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        offset = -Long.BYTES;
                    }

                } else {

                    break;
                }
            }
        }

        return tagTransactions;
    }

    public static synchronized long approveePointer(final byte[] hash) {

        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11;
        for (int depth = 2; depth < Transaction.HASH_SIZE; depth++) {

            ((ByteBuffer)approversChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {

                    return 0;
                }

            } else {

                for (; depth < Transaction.HASH_SIZE; depth++) {

                    if (mainBuffer[Transaction.HASH_OFFSET + depth] != hash[depth]) {

                        return 0;
                    }
                }

                return pointer;
            }
        }

        throw new IllegalStateException("Corrupted storage");
    }

    public static synchronized List<Long> approveeTransactions(final long pointer) {

        final List<Long> approveeTransactions = new LinkedList<>();

        if (pointer != 0) {

            ((ByteBuffer) approversChunks[(int) (pointer >> 27)].position((int) (pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
            int offset = ZEROTH_POINTER_OFFSET - Long.BYTES;
            while (true) {

                while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES) {

                    final long transactionPointer = value(mainBuffer, offset);
                    if (transactionPointer == 0) {

                        break;

                    } else {

                        approveeTransactions.add(transactionPointer);
                    }
                }
                if (offset == CELL_SIZE - Long.BYTES) {

                    final long nextCellPointer = value(mainBuffer, offset);
                    if (nextCellPointer == 0) {

                        break;

                    } else {

                        ((ByteBuffer) approversChunks[(int) (nextCellPointer >> 27)].position((int) (nextCellPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        offset = -Long.BYTES;
                    }

                } else {

                    break;
                }
            }
        }

        return approveeTransactions;
    }

    public static synchronized void setTransactionValidity(final long pointer, final int validity) {

        transactionsChunks[(int)(pointer >> 27)].put(((int)(pointer & (CHUNK_SIZE - 1))) + Transaction.VALIDITY_OFFSET, (byte)validity);
    }

    public static long value(final byte[] buffer, final int offset) {

        return ((long)(buffer[offset] & 0xFF)) + (((long)(buffer[offset + 1] & 0xFF)) << 8) + (((long)(buffer[offset + 2] & 0xFF)) << 16) + (((long)(buffer[offset + 3] & 0xFF)) << 24) + (((long)(buffer[offset + 4] & 0xFF)) << 32) + (((long)(buffer[offset + 5] & 0xFF)) << 40) + (((long)(buffer[offset + 6] & 0xFF)) << 48) + (((long)(buffer[offset + 7] & 0xFF)) << 56);
    }

    public static void setValue(final byte[] buffer, final int offset, final long value) {

        buffer[offset] = (byte)value;
        buffer[offset + 1] = (byte)(value >> 8);
        buffer[offset + 2] = (byte)(value >> 16);
        buffer[offset + 3] = (byte)(value >> 24);
        buffer[offset + 4] = (byte)(value >> 32);
        buffer[offset + 5] = (byte)(value >> 40);
        buffer[offset + 6] = (byte)(value >> 48);
        buffer[offset + 7] = (byte)(value >> 56);
    }

    private static void appendToTransactions(final boolean tip) {

        ((ByteBuffer)transactionsChunks[(int)(transactionsNextPointer >> 27)].position((int)(transactionsNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

        if (tip) {

            final long index = (transactionsNextPointer - (CELLS_OFFSET - SUPER_GROUPS_OFFSET)) >> 11;
            transactionsTipsFlags.put((int) (index >> 3), (byte) (transactionsTipsFlags.get((int) (index >> 3)) | (1 << (index & 7))));
        }

        if (((transactionsNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {

                transactionsChunks[(int)(transactionsNextPointer >> 27)] = transactionsChannel.map(FileChannel.MapMode.READ_WRITE, SUPER_GROUPS_OFFSET + transactionsNextPointer, CHUNK_SIZE);

            } catch (final IOException e) {

                e.printStackTrace();
            }
        }
    }

    private static void appendToBundles() {

        ((ByteBuffer)bundlesChunks[(int)(bundlesNextPointer >> 27)].position((int)(bundlesNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((bundlesNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {

                bundlesChunks[(int)(bundlesNextPointer >> 27)] = bundlesChannel.map(FileChannel.MapMode.READ_WRITE, bundlesNextPointer, CHUNK_SIZE);

            } catch (final IOException e) {

                e.printStackTrace();
            }
        }
    }

    private static void appendToAddresses() {

        ((ByteBuffer)addressesChunks[(int)(addressesNextPointer >> 27)].position((int)(addressesNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((addressesNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {

                addressesChunks[(int)(addressesNextPointer >> 27)] = addressesChannel.map(FileChannel.MapMode.READ_WRITE, addressesNextPointer, CHUNK_SIZE);

            } catch (final IOException e) {

                e.printStackTrace();
            }
        }
    }

    private static void appendToTags() {

        ((ByteBuffer) tagsChunks[(int)(tagsNextPointer >> 27)].position((int)(tagsNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((tagsNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {

                tagsChunks[(int)(tagsNextPointer >> 27)] = tagsChannel.map(FileChannel.MapMode.READ_WRITE, tagsNextPointer, CHUNK_SIZE);

            } catch (final IOException e) {

                e.printStackTrace();
            }
        }
    }

    private static void appendToApprovers() {

        ((ByteBuffer)approversChunks[(int)(approversNextPointer >> 27)].position((int)(approversNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((approversNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {

                approversChunks[(int)(approversNextPointer >> 27)] = approversChannel.map(FileChannel.MapMode.READ_WRITE, approversNextPointer, CHUNK_SIZE);

            } catch (final IOException e) {

                e.printStackTrace();
            }
        }
    }

    private static void updateBundleAddressTagAndApprovers(final long transactionPointer) {

        final Transaction transaction = new Transaction(mainBuffer, transactionPointer);
        for (int j = 0; j < numberOfApprovedTransactionsToStore; j++) {

            storeTransaction(approvedTransactionsToStore[j], null, false);
        }
        numberOfApprovedTransactionsToStore = 0;

        {
            long pointer = ((transaction.bundle[0] + 128) + ((transaction.bundle[1] + 128) << 8)) << 11, prevPointer = 0;
            for (int depth = 2; depth < Transaction.BUNDLE_SIZE; depth++) {

                ((ByteBuffer)bundlesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

                if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                    prevPointer = pointer;
                    if ((pointer = value(mainBuffer, (transaction.bundle[depth] + 128) << 3)) == 0) {

                        setValue(mainBuffer, (transaction.bundle[depth] + 128) << 3, bundlesNextPointer);
                        ((ByteBuffer)bundlesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                        mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                        System.arraycopy(transaction.bundle, 0, mainBuffer, 8, Transaction.BUNDLE_SIZE);
                        setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                        appendToBundles();

                        break;
                    }

                } else {

                    boolean sameBundle = true;

                    for (int i = depth; i < Transaction.BUNDLE_SIZE; i++) {

                        if (mainBuffer[Transaction.HASH_OFFSET + i] != transaction.bundle[i]) {

                            final int differentHashByte = mainBuffer[Transaction.HASH_OFFSET + i];

                            ((ByteBuffer)bundlesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                            setValue(mainBuffer, (transaction.bundle[depth - 1] + 128) << 3, bundlesNextPointer);
                            ((ByteBuffer)bundlesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                            for (int j = depth; j < i; j++) {

                                System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                setValue(mainBuffer, (transaction.bundle[j] + 128) << 3, bundlesNextPointer + CELL_SIZE);
                                appendToBundles();
                            }

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            setValue(mainBuffer, (differentHashByte + 128) << 3, pointer);
                            setValue(mainBuffer, (transaction.bundle[i] + 128) << 3, bundlesNextPointer + CELL_SIZE);
                            appendToBundles();

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                            System.arraycopy(transaction.bundle, 0, mainBuffer, 8, Transaction.BUNDLE_SIZE);
                            setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                            appendToBundles();

                            sameBundle = false;

                            break;
                        }
                    }

                    if (sameBundle) {

                        int offset = ZEROTH_POINTER_OFFSET;
                        while (true) {

                            while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES && value(mainBuffer, offset) != 0) {

                                // Do nothing
                            }
                            if (offset == CELL_SIZE - Long.BYTES) {

                                final long nextCellPointer = value(mainBuffer, offset);
                                if (nextCellPointer == 0) {

                                    setValue(mainBuffer, offset, bundlesNextPointer);
                                    ((ByteBuffer)bundlesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                    System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                    setValue(mainBuffer, 0, transactionPointer);
                                    appendToBundles();

                                    break;

                                } else {

                                    pointer = nextCellPointer;
                                    ((ByteBuffer)bundlesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                                    offset = -Long.BYTES;
                                }

                            } else {

                                setValue(mainBuffer, offset, transactionPointer);
                                ((ByteBuffer)bundlesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                break;
                            }
                        }
                    }

                    break;
                }
            }
        }

        {
            long pointer = ((transaction.address[0] + 128) + ((transaction.address[1] + 128) << 8)) << 11, prevPointer = 0;
            for (int depth = 2; depth < Transaction.ADDRESS_SIZE; depth++) {

                ((ByteBuffer)addressesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

                if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                    prevPointer = pointer;
                    if ((pointer = value(mainBuffer, (transaction.address[depth] + 128) << 3)) == 0) {

                        setValue(mainBuffer, (transaction.address[depth] + 128) << 3, addressesNextPointer);
                        ((ByteBuffer)addressesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                        mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                        System.arraycopy(transaction.address, 0, mainBuffer, 8, Transaction.ADDRESS_SIZE);
                        setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                        appendToAddresses();

                        break;
                    }

                } else {

                    boolean sameAddress = true;

                    for (int i = depth; i < Transaction.ADDRESS_SIZE; i++) {

                        if (mainBuffer[Transaction.HASH_OFFSET + i] != transaction.address[i]) {

                            final int differentHashByte = mainBuffer[Transaction.HASH_OFFSET + i];

                            ((ByteBuffer)addressesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                            setValue(mainBuffer, (transaction.address[depth - 1] + 128) << 3, addressesNextPointer);
                            ((ByteBuffer)addressesChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                            for (int j = depth; j < i; j++) {

                                System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                setValue(mainBuffer, (transaction.address[j] + 128) << 3, addressesNextPointer + CELL_SIZE);
                                appendToAddresses();
                            }

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            setValue(mainBuffer, (differentHashByte + 128) << 3, pointer);
                            setValue(mainBuffer, (transaction.address[i] + 128) << 3, addressesNextPointer + CELL_SIZE);
                            appendToAddresses();

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                            System.arraycopy(transaction.address, 0, mainBuffer, 8, Transaction.ADDRESS_SIZE);
                            setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                            appendToAddresses();

                            sameAddress = false;

                            break;
                        }
                    }

                    if (sameAddress) {

                        int offset = ZEROTH_POINTER_OFFSET;
                        while (true) {

                            while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES && value(mainBuffer, offset) != 0) {

                                // Do nothing
                            }
                            if (offset == CELL_SIZE - Long.BYTES) {

                                final long nextCellPointer = value(mainBuffer, offset);
                                if (nextCellPointer == 0) {

                                    setValue(mainBuffer, offset, addressesNextPointer);
                                    ((ByteBuffer)addressesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                    System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                    setValue(mainBuffer, 0, transactionPointer);
                                    appendToAddresses();

                                    break;

                                } else {

                                    pointer = nextCellPointer;
                                    ((ByteBuffer)addressesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                                    offset = -Long.BYTES;
                                }

                            } else {

                                setValue(mainBuffer, offset, transactionPointer);
                                ((ByteBuffer)addressesChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                break;
                            }
                        }
                    }

                    break;
                }
            }
        }

        for (int i = 0; i < Transaction.TAG_SIZE; i++) {

            if (transaction.tag[i] != 0) {

                long pointer = ((transaction.tag[0] + 128) + ((transaction.tag[1] + 128) << 8)) << 11, prevPointer = 0;
                for (int depth = 2; depth < Transaction.TAG_SIZE; depth++) {

                    ((ByteBuffer) tagsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

                    if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                        prevPointer = pointer;
                        if ((pointer = value(mainBuffer, (transaction.tag[depth] + 128) << 3)) == 0) {

                            setValue(mainBuffer, (transaction.tag[depth] + 128) << 3, tagsNextPointer);
                            ((ByteBuffer) tagsChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                            System.arraycopy(transaction.tag, 0, mainBuffer, 8, Transaction.TAG_SIZE);
                            setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                            appendToTags();

                            break;
                        }

                    } else {

                        boolean sameTag = true;

                        for (int j = depth; j < Transaction.TAG_SIZE; j++) {

                            if (mainBuffer[Transaction.HASH_OFFSET + j] != transaction.tag[j]) {

                                final int differentHashByte = mainBuffer[Transaction.HASH_OFFSET + j];

                                ((ByteBuffer) tagsChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                                setValue(mainBuffer, (transaction.tag[depth - 1] + 128) << 3, tagsNextPointer);
                                ((ByteBuffer) tagsChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                for (int k = depth; k < j; k++) {

                                    System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                    setValue(mainBuffer, (transaction.tag[k] + 128) << 3, tagsNextPointer + CELL_SIZE);
                                    appendToTags();
                                }

                                System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                setValue(mainBuffer, (differentHashByte + 128) << 3, pointer);
                                setValue(mainBuffer, (transaction.tag[j] + 128) << 3, tagsNextPointer + CELL_SIZE);
                                appendToTags();

                                System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                                System.arraycopy(transaction.tag, 0, mainBuffer, 8, Transaction.TAG_SIZE);
                                setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                                appendToTags();

                                sameTag = false;

                                break;
                            }
                        }

                        if (sameTag) {

                            int offset = ZEROTH_POINTER_OFFSET;
                            while (true) {

                                while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES && value(mainBuffer, offset) != 0) {

                                    // Do nothing
                                }
                                if (offset == CELL_SIZE - Long.BYTES) {

                                    final long nextCellPointer = value(mainBuffer, offset);
                                    if (nextCellPointer == 0) {

                                        setValue(mainBuffer, offset, tagsNextPointer);
                                        ((ByteBuffer) tagsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                                        setValue(mainBuffer, 0, transactionPointer);
                                        appendToTags();

                                        break;

                                    } else {

                                        pointer = nextCellPointer;
                                        ((ByteBuffer) tagsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                                        offset = -Long.BYTES;
                                    }

                                } else {

                                    setValue(mainBuffer, offset, transactionPointer);
                                    ((ByteBuffer) tagsChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                                    break;
                                }
                            }
                        }

                        break;
                    }
                }

                break;
            }
        }

        updateApprover(transaction.trunkTransaction, transactionPointer);
        if (transaction.branchTransactionPointer != transaction.trunkTransactionPointer) {

            updateApprover(transaction.branchTransaction, transactionPointer);
        }
    }

    private static void updateApprover(final byte[] hash, final long transactionPointer) {

        long pointer = ((hash[0] + 128) + ((hash[1] + 128) << 8)) << 11, prevPointer = 0;
        for (int depth = 2; depth < Transaction.HASH_SIZE; depth++) {

            ((ByteBuffer)approversChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);

            if (mainBuffer[Transaction.TYPE_OFFSET] == GROUP) {

                prevPointer = pointer;
                if ((pointer = value(mainBuffer, (hash[depth] + 128) << 3)) == 0) {

                    setValue(mainBuffer, (hash[depth] + 128) << 3, approversNextPointer);
                    ((ByteBuffer)approversChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                    System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                    mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                    System.arraycopy(hash, 0, mainBuffer, 8, Transaction.HASH_SIZE);
                    setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                    appendToApprovers();

                    return;
                }

            } else {

                for (int i = depth; i < Transaction.HASH_SIZE; i++) {

                    if (mainBuffer[Transaction.HASH_OFFSET + i] != hash[i]) {

                        final int differentHashByte = mainBuffer[Transaction.HASH_OFFSET + i];

                        ((ByteBuffer)approversChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                        setValue(mainBuffer, (hash[depth - 1] + 128) << 3, approversNextPointer);
                        ((ByteBuffer)approversChunks[(int)(prevPointer >> 27)].position((int)(prevPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                        for (int j = depth; j < i; j++) {

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            setValue(mainBuffer, (hash[j] + 128) << 3, approversNextPointer + CELL_SIZE);
                            appendToApprovers();
                        }

                        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                        setValue(mainBuffer, (differentHashByte + 128) << 3, pointer);
                        setValue(mainBuffer, (hash[i] + 128) << 3, approversNextPointer + CELL_SIZE);
                        appendToApprovers();

                        System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                        mainBuffer[Transaction.TYPE_OFFSET] = FILLED_SLOT;
                        System.arraycopy(hash, 0, mainBuffer, 8, Transaction.HASH_SIZE);
                        setValue(mainBuffer, ZEROTH_POINTER_OFFSET, transactionPointer);
                        appendToApprovers();

                        return;
                    }
                }

                int offset = ZEROTH_POINTER_OFFSET;
                while (true) {

                    while ((offset += Long.BYTES) < CELL_SIZE - Long.BYTES && value(mainBuffer, offset) != 0) {

                        // Do nothing
                    }
                    if (offset == CELL_SIZE - Long.BYTES) {

                        final long nextCellPointer = value(mainBuffer, offset);
                        if (nextCellPointer == 0) {

                            setValue(mainBuffer, offset, approversNextPointer);
                            ((ByteBuffer)approversChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                            System.arraycopy(ZEROED_BUFFER, 0, mainBuffer, 0, CELL_SIZE);
                            setValue(mainBuffer, 0, transactionPointer);
                            appendToApprovers();

                            return;

                        } else {

                            pointer = nextCellPointer;
                            ((ByteBuffer)approversChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).get(mainBuffer);
                            offset = -Long.BYTES;
                        }

                    } else {

                        setValue(mainBuffer, offset, transactionPointer);
                        ((ByteBuffer)approversChunks[(int)(pointer >> 27)].position((int)(pointer & (CHUNK_SIZE - 1)))).put(mainBuffer);

                        return;
                    }
                }
            }
        }
    }
}
