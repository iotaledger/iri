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

public class Storage extends AbstractStorage {
	
	private static final Logger log = LoggerFactory.getLogger(Storage.class);

    private static final String SCRATCHPAD_FILE_NAME = "scratchpad.iri";

    public static final byte[][] approvedTransactionsToStore = new byte[2][];
    
    public static int numberOfApprovedTransactionsToStore;
    
    private static ByteBuffer transactionsToRequest;
    public volatile static int numberOfTransactionsToRequest;
    private static final byte[] transactionToRequest = new byte[Transaction.HASH_SIZE];

    public static ByteBuffer analyzedTransactionsFlags, analyzedTransactionsFlagsCopy;

    private volatile boolean launched;

    private static final Object transactionToRequestMonitor = new Object();
    private static int previousNumberOfTransactions;
    
    private StorageTransactions storageTransactionInstance = StorageTransactions.instance();
    private StorageBundle storageBundleInstance = StorageBundle.instance();
    private StorageAddresses storageAddressesInstance = StorageAddresses.instance();
    private StorageTags storageTags = StorageTags.instance();
    private StorageApprovers storageApprovers = StorageApprovers.instance();
    
    @Override
    public synchronized void init() throws IOException {
    	
    	storageTransactionInstance.init();
    	storageBundleInstance.init();
    	storageAddressesInstance.init();
    	storageTags.init();
    	storageApprovers.init();
    	
    	storageTransactionInstance.updateBundleAddressTagApprovers();

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
        	storageAddressesInstance.shutdown();
        	storageTags.shutdown();
        	storageApprovers.shutdown();
        	
            log.info("DB successfully flushed");
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

    void updateBundleAddressTagAndApprovers(final long transactionPointer) {

        final Transaction transaction = new Transaction(mainBuffer, transactionPointer);
        for (int j = 0; j < numberOfApprovedTransactionsToStore; j++) {
            StorageTransactions.instance().storeTransaction(approvedTransactionsToStore[j], null, false);
        }
        numberOfApprovedTransactionsToStore = 0;

        StorageBundle.instance().updateBundle(transactionPointer, transaction);
        StorageAddresses.instance().updateAddresses(transactionPointer, transaction);
        StorageTags.instance().updateTags(transactionPointer, transaction);
        StorageApprovers.instance().updateApprover(transaction.trunkTransaction, transactionPointer);
        
        if (transaction.branchTransactionPointer != transaction.trunkTransactionPointer) {
        	StorageApprovers.instance().updateApprover(transaction.branchTransaction, transactionPointer);
        }
    }
    
    // methods helper
    
    private static Storage instance = new Storage();
    
    private Storage() {}
    
    public static Storage instance() {
		return instance;
	}
}

