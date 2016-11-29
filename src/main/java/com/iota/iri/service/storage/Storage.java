package com.iota.iri.service.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;

public class Storage extends AbstractStorage {
	
	private static final Logger log = LoggerFactory.getLogger(Storage.class);

    private static final String ADDRESSES_FILE_NAME = "addresses.iri";
    private static final String TAGS_FILE_NAME = "tags.iri";
    private static final String APPROVERS_FILE_NAME = "approvers.iri";
    private static final String SCRATCHPAD_FILE_NAME = "scratchpad.iri";

    public static final byte[][] approvedTransactionsToStore = new byte[2][];
    
    public static int numberOfApprovedTransactionsToStore;

    private FileChannel addressesChannel;
    private final ByteBuffer[] addressesChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
    private volatile long addressesNextPointer = SUPER_GROUPS_SIZE;

    private FileChannel tagsChannel;
    private final ByteBuffer[] tagsChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
    private volatile long tagsNextPointer = SUPER_GROUPS_SIZE;

    private FileChannel approversChannel;
    private final ByteBuffer[] approversChunks = new ByteBuffer[MAX_NUMBER_OF_CHUNKS];
    private volatile long approversNextPointer = SUPER_GROUPS_SIZE;

    private static ByteBuffer transactionsToRequest;
    public volatile static int numberOfTransactionsToRequest;
    private static final byte[] transactionToRequest = new byte[Transaction.HASH_SIZE];

    public static ByteBuffer analyzedTransactionsFlags, analyzedTransactionsFlagsCopy;

    private volatile boolean launched;

    private static final Object transactionToRequestMonitor = new Object();
    private static int previousNumberOfTransactions;
    
    private StorageTransactions storageTransactionInstance = StorageTransactions.instance();
    private StorageBundle storageBundleInstance = StorageBundle.instance();

    @Override
    public synchronized void init() throws IOException {
    	
    	storageTransactionInstance.init();
    	storageBundleInstance.init();
    	
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

        launched = true;
    }

    @Override
    public synchronized void shutdown() {

        if (launched) {

        	storageTransactionInstance.shutdown();
        	storageBundleInstance.shutdown();
        	

            for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && addressesChunks[i] != null; i++) {
            	log.info("Flushing addresses chunk #" + i);
                flush(addressesChunks[i]);
            }

            for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && tagsChunks[i] != null; i++) {
            	log.info("Flushing tags chunk #" + i);
                flush(tagsChunks[i]);
            }

            for (int i = 0; i < MAX_NUMBER_OF_CHUNKS && approversChunks[i] != null; i++) {
            	log.info("Flushing approvers chunk #" + i);
                flush(approversChunks[i]);
            }

            log.info("DB successfully flushed");

            try {
                addressesChannel.close();
                tagsChannel.close();
                approversChannel.close();

            } catch (final Exception e) {
            	log.error("Catched Exception whilst shutting down:", e);
            }
        }
    }

    public void transactionToRequest(final byte[] buffer, final int offset) {

        synchronized (transactionToRequestMonitor) {

            if (numberOfTransactionsToRequest == 0) {

                final long beginningTime = System.currentTimeMillis();

                synchronized (analyzedTransactionsFlags) {

                    clearAnalyzedTransactionsFlags();

                    final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(
                    		Collections.singleton(
                    				StorageTransactions.instance()
                    					.transactionPointer(Milestone.latestMilestone.bytes())));
                    
                    Long pointer;
                    while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                        if (Storage.instance().setAnalyzedTransactionFlag(pointer)) {

                            final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
                            if (transaction.type == Storage.PREFILLED_SLOT) {

                                ((ByteBuffer) transactionsToRequest.position(numberOfTransactionsToRequest++ * Transaction.HASH_SIZE)).put(transaction.hash); // Only 2'917'776 hashes can be stored this way without overflowing the buffer, we assume that nodes will never need to store that many hashes, so we don't need to cap "numberOfTransactionsToRequest"
                            } else {
                                nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                                nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);
                            }
                        }
                    }
                }

                final long transactionsNextPointer = StorageTransactions.transactionsNextPointer;
				log.info("Transactions to request = {}", numberOfTransactionsToRequest + " / " + (transactionsNextPointer - (CELLS_OFFSET - SUPER_GROUPS_OFFSET)) / CELL_SIZE + " (" + (System.currentTimeMillis() - beginningTime) + " ms / " + (numberOfTransactionsToRequest == 0 ? 0 : (previousNumberOfTransactions == 0 ? 0 : (((transactionsNextPointer - (CELLS_OFFSET - SUPER_GROUPS_OFFSET)) / CELL_SIZE - previousNumberOfTransactions) * 100) / numberOfTransactionsToRequest)) + "%)");
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

    public void clearAnalyzedTransactionsFlags() {

        analyzedTransactionsFlags.position(0);
        for (int i = 0; i < ANALYZED_TRANSACTIONS_FLAGS_SIZE / CELL_SIZE; i++) {
            analyzedTransactionsFlags.put(ZEROED_BUFFER);
        }
    }

    public boolean analyzedTransactionFlag(long pointer) {

        pointer -= CELLS_OFFSET - SUPER_GROUPS_OFFSET;
        return (analyzedTransactionsFlags.get((int) (pointer >> (11 + 3))) & (1 << ((pointer >> 11) & 7))) != 0;
    }

    public boolean setAnalyzedTransactionFlag(long pointer) {

        pointer -= CELLS_OFFSET - SUPER_GROUPS_OFFSET;

        final int value = analyzedTransactionsFlags.get((int) (pointer >> (11 + 3)));
        if ((value & (1 << ((pointer >> 11) & 7))) == 0) {
            analyzedTransactionsFlags.put((int)(pointer >> (11 + 3)), (byte)(value | (1 << ((pointer >> 11) & 7))));
            return true;
        } 
        return false;
    }

    public void saveAnalyzedTransactionsFlags() {

        analyzedTransactionsFlags.position(0);
        analyzedTransactionsFlagsCopy.position(0);
        analyzedTransactionsFlagsCopy.put(analyzedTransactionsFlags);
    }

    public void loadAnalyzedTransactionsFlags() {

        analyzedTransactionsFlagsCopy.position(0);
        analyzedTransactionsFlags.position(0);
        analyzedTransactionsFlags.put(analyzedTransactionsFlagsCopy);
    }

    public synchronized long addressPointer(final byte[] hash) {

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

    public synchronized List<Long> addressTransactions(final long pointer) {

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

    public synchronized long tagPointer(final byte[] hash) {

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

    public synchronized List<Long> tagTransactions(final long pointer) {

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

    public synchronized long approveePointer(final byte[] hash) {

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

    public synchronized List<Long> approveeTransactions(final long pointer) {

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

    private void appendToAddresses() {

        ((ByteBuffer)addressesChunks[(int)(addressesNextPointer >> 27)].position((int)(addressesNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((addressesNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {
                addressesChunks[(int)(addressesNextPointer >> 27)] = addressesChannel.map(FileChannel.MapMode.READ_WRITE, addressesNextPointer, CHUNK_SIZE);
            } catch (final IOException e) {
            	log.error("Caught exception on appendToAddresses:", e);
            }
        }
    }

    private void appendToTags() {

        ((ByteBuffer) tagsChunks[(int)(tagsNextPointer >> 27)].position((int)(tagsNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((tagsNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {

                tagsChunks[(int)(tagsNextPointer >> 27)] = tagsChannel.map(FileChannel.MapMode.READ_WRITE, tagsNextPointer, CHUNK_SIZE);

            } catch (final IOException e) {
            	log.error("Caught exception on appendToTags:", e);
            }
        }
    }

    private void appendToApprovers() {

        ((ByteBuffer)approversChunks[(int)(approversNextPointer >> 27)].position((int)(approversNextPointer & (CHUNK_SIZE - 1)))).put(mainBuffer);
        if (((approversNextPointer += CELL_SIZE) & (CHUNK_SIZE - 1)) == 0) {

            try {
                approversChunks[(int)(approversNextPointer >> 27)] = approversChannel.map(FileChannel.MapMode.READ_WRITE, approversNextPointer, CHUNK_SIZE);
            } catch (final IOException e) {
            	log.error("Caught exception on appendToApprovers:", e);
            }
        }
    }

    void updateBundleAddressTagAndApprovers(final long transactionPointer) {

        final Transaction transaction = new Transaction(mainBuffer, transactionPointer);
        for (int j = 0; j < numberOfApprovedTransactionsToStore; j++) {
            StorageTransactions.instance().storeTransaction(approvedTransactionsToStore[j], null, false);
        }
        numberOfApprovedTransactionsToStore = 0;

        StorageBundle.instance().updateBundle(transactionPointer, transaction);

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

    private void updateApprover(final byte[] hash, final long transactionPointer) {

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
    
    // methods helpes
    
    public byte[] mainBuffer() {
		return mainBuffer;
	}
    
    public byte[] auxBuffer() {
		return auxBuffer;
	}
    
    public byte[] zeroedBuffer() {
		return ZEROED_BUFFER;
	}
    
    private static Storage instance = new Storage();
    
    private Storage() {}
    
    public static Storage instance() {
		return instance;
	}

}

