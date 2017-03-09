package com.iota.iri.service.storage;

import java.io.IOException;

import com.iota.iri.service.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        final TransactionViewModel transactionViewModel = new TransactionViewModel(mainBuffer, transactionPointer);
        for (int j = 0; j < numberOfApprovedTransactionsToStore; j++) {
            StorageTransactions.instance().storeTransaction(approvedTransactionsToStore[j], null, false);
        }
        numberOfApprovedTransactionsToStore = 0;

        StorageBundle.instance().updateBundle(transactionPointer, transactionViewModel);
        StorageAddresses.instance().updateAddresses(transactionPointer, transactionViewModel);
        StorageTags.instance().updateTags(transactionPointer, transactionViewModel);
        StorageApprovers.instance().updateApprover(transactionViewModel.getTrunkTransactionHash(), transactionPointer);
        
        if (transactionViewModel.branchTransactionPointer != transactionViewModel.trunkTransactionPointer) {
        	StorageApprovers.instance().updateApprover(transactionViewModel.getBranchTransactionHash(), transactionPointer);
        }
    }
    
    // methods helper
    
    private static Storage instance = new Storage();
    
    private Storage() {}
    
    public static Storage instance() {
		return instance;
	}
}

