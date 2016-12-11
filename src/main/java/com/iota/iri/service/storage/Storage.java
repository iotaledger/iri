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

/**
 * Storage is organized as 243-value tree
 */
public class Storage extends AbstractStorage {
	
    private static final Logger log = LoggerFactory.getLogger(Storage.class);

    public static final byte[][] approvedTransactionsToStore = new byte[2][];

    private volatile boolean launched;

    public static int numberOfApprovedTransactionsToStore;

    private StorageTransactions storageTransactionInstance = StorageTransactions.instance();
    private StorageBundle storageBundleInstance = StorageBundle.instance();
    private StorageAddresses storageAddressesInstance = StorageAddresses.instance();
    private StorageTags storageTags = StorageTags.instance();
    private StorageApprovers storageApprovers = StorageApprovers.instance();
    private StorageScratchpad storageScratchpad = StorageScratchpad.instance();

    @Override
    public void init() throws IOException {

        synchronized (Storage.class) {
            storageTransactionInstance.init();
            storageBundleInstance.init();
            storageAddressesInstance.init();
            storageTags.init();
            storageApprovers.init();
            storageScratchpad.init();
            storageTransactionInstance.updateBundleAddressTagApprovers();
            launched = true;
        }
    }

    @Override
    public void shutdown() {

        synchronized (Storage.class) {
            if (launched) {
                storageTransactionInstance.shutdown();
                storageBundleInstance.shutdown();
                storageAddressesInstance.shutdown();
                storageTags.shutdown();
                storageApprovers.shutdown();
                storageScratchpad.shutdown();

                log.info("DB successfully flushed");
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

