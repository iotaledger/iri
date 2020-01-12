package com.iota.iri.profiling;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.controllers.*;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDbSizeProfiling {

    private static final Logger log = LoggerFactory.getLogger(RocksDbSizeProfiling.class);

    @Test
    public void profileDBSectionsSize() throws Exception {

        String dbPath = System.getProperty("dbpath", "mainnetdb");

        RocksDBPersistenceProvider localRocksDBPP = new RocksDBPersistenceProvider(dbPath, dbPath + ".log", null,
                BaseIotaConfig.Defaults.DB_CACHE_SIZE, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);

        Tangle localTangle = new Tangle();
        localTangle.addPersistenceProvider(localRocksDBPP);
        localTangle.init();

        long counter;
        long size;
        List<Long> sizes = new LinkedList<>();

        // scan the whole DB to get the size of all the components:
        TransactionViewModel tx = TransactionViewModel.first(localTangle);
        counter = 0;
        while (tx != null) {
            if (++counter % 10000 == 0) {
                log.info("Scanned {} Transactions", counter);
            }
            tx = tx.next(localTangle);
        }
        // Key + Value + Metadata
        sizes.add(counter * (Hash.SIZE_IN_BYTES + Transaction.SIZE + 450));

        AddressViewModel add = AddressViewModel.first(localTangle);
        counter = 0;
        size = 0;
        while (add != null) {
            if (++counter % 10000 == 0) {
                log.info("Scanned {} Addresses", counter);
            }
            size += add.size();
            add = add.next(localTangle);
        }
        // Key + # of entries in each value ( + delimiter)
        sizes.add(counter * Hash.SIZE_IN_BYTES + size * (Hash.SIZE_IN_BYTES + 1));

        TagViewModel tag = TagViewModel.first(localTangle);
        counter = 0;
        size = 0;
        while (tag != null) {
            if (++counter % 10000 == 0) {
                log.info("Scanned {} Tags", counter);
            }
            size += tag.size();
            tag = tag.next(localTangle);
        }
        // Key + # of entries in each value ( + delimiter)
        sizes.add(counter * Hash.SIZE_IN_BYTES + size * (Hash.SIZE_IN_BYTES + 1));

        ApproveeViewModel approvee = ApproveeViewModel.first(localTangle);
        counter = 0;
        size = 0;
        while (approvee != null) {
            if (++counter % 10000 == 0) {
                log.info("Scanned {} Approvees", counter);
            }
            size += approvee.size();
            approvee = approvee.next(localTangle);
        }
        // Key + # of entries in each value ( + delimiter)
        sizes.add(counter * Hash.SIZE_IN_BYTES + size * (Hash.SIZE_IN_BYTES + 1));

        ApproveeViewModel bundle = ApproveeViewModel.first(localTangle);
        counter = 0;
        size = 0;
        while (bundle != null) {
            if (++counter % 10000 == 0) {
                log.info("Scanned {} Bundles", counter);
            }
            size += bundle.size();
            bundle = bundle.next(localTangle);
        }
        // Key + # of entries in each value ( + delimiter)
        sizes.add(counter * Hash.SIZE_IN_BYTES + size * (Hash.SIZE_IN_BYTES + 1));

        MilestoneViewModel milestone = MilestoneViewModel.first(localTangle);
        counter = 0;
        while (milestone != null) {
            if (++counter % 10000 == 0) {
                log.info("Scanned {} Bundles", counter);
            }
            milestone = milestone.next(localTangle);
        }
        sizes.add(counter * (Integer.BYTES + Hash.SIZE_IN_BYTES));

        MilestoneViewModel milestoneForStateDiff = MilestoneViewModel.first(localTangle);
        counter = 0;
        size = 0;
        while (milestoneForStateDiff != null) {
            if (++counter % 10000 == 0) {
                log.info("Scanned {} StateDiffs", counter);
            }
            StateDiffViewModel stateDiff = StateDiffViewModel.load(localTangle, milestoneForStateDiff.getHash());
            size += stateDiff.getDiff().size();
            milestoneForStateDiff = milestoneForStateDiff.next(localTangle);
        }

        sizes.add(counter * (Long.BYTES + Hash.SIZE_IN_BYTES) + size * (Long.BYTES + Hash.SIZE_IN_BYTES));

        double sum = sizes.stream().reduce((a, b) -> a + b).get();
        int i = 0;
        log.info("----------------------------");
        log.info(String.format("transactionBytes: %.2f", sizes.get(i++) / sum * 100));
        log.info(String.format("addressBytes: %.2f", sizes.get(i++) / sum * 100));
        log.info(String.format("tagBytes: %.2f", sizes.get(i++) / sum * 100));
        log.info(String.format("approveeBytes: %.2f", sizes.get(i++) / sum * 100));
        log.info(String.format("bundleBytes: %.2f", sizes.get(i++) / sum * 100));
        log.info(String.format("milestoneBytes: %.2f", sizes.get(i++) / sum * 100));
        log.info(String.format("stateDiffBytes: %.2f", sizes.get(i++) / sum * 100));
        log.info(String.format("Total (uncompressed): %.2f GB", sum / 1073741824 /* GB */));
    }
}
