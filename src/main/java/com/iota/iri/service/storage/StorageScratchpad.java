package com.iota.iri.service.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;

public class StorageScratchpad extends AbstractStorage {

    private static final Logger log = LoggerFactory.getLogger(StorageScratchpad.class);

    private static final StorageScratchpad instance = new StorageScratchpad();
    private static final String SCRATCHPAD_FILE_NAME = "scratchpad.iri";

    private ByteBuffer transactionsToRequest;
    private ByteBuffer analyzedTransactionsFlags, analyzedTransactionsFlagsCopy;
    
    private final byte[] transactionToRequest = new byte[Transaction.HASH_SIZE];
    private final Object transactionToRequestMonitor = new Object();
    private int previousNumberOfTransactions;

    public volatile int numberOfTransactionsToRequest;

    private FileChannel scratchpadChannel = null;

    @Override
    public void init() throws IOException {
        scratchpadChannel = FileChannel.open(Paths.get(SCRATCHPAD_FILE_NAME), StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        transactionsToRequest = scratchpadChannel.map(FileChannel.MapMode.READ_WRITE, TRANSACTIONS_TO_REQUEST_OFFSET, TRANSACTIONS_TO_REQUEST_SIZE);
        analyzedTransactionsFlags = scratchpadChannel.map(FileChannel.MapMode.READ_WRITE, ANALYZED_TRANSACTIONS_FLAGS_OFFSET, ANALYZED_TRANSACTIONS_FLAGS_SIZE);
        analyzedTransactionsFlagsCopy = scratchpadChannel.map(FileChannel.MapMode.READ_WRITE, ANALYZED_TRANSACTIONS_FLAGS_COPY_OFFSET, ANALYZED_TRANSACTIONS_FLAGS_COPY_SIZE);	
    }

    @Override
    public void shutdown() {
        try {
            scratchpadChannel.close();	
        } catch (final Exception e) {
            log.error("Shutting down Storage Scratchpad error: ", e);
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

                        if (setAnalyzedTransactionFlag(pointer)) {

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
    
    public ByteBuffer getAnalyzedTransactionsFlags() {
		return analyzedTransactionsFlags;
	}
    
    public ByteBuffer getAnalyzedTransactionsFlagsCopy() {
		return analyzedTransactionsFlagsCopy;
	}
    
    public int getNumberOfTransactionsToRequest() {
		return numberOfTransactionsToRequest;
	}

	public static StorageScratchpad instance() {
		return instance;
	}
	
}
