package com.iota.iri.storage;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Tag;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotProviderImpl;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.Random;
import java.util.Set;

public class TangleTest {
    private final TemporaryFolder dbFolder = new TemporaryFolder();
    private final TemporaryFolder logFolder = new TemporaryFolder();
    private Tangle tangle = new Tangle();
    private static SnapshotProvider snapshotProvider;

    private static final Random seed = new Random();

    @Before
    public void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider;
        rocksDBPersistenceProvider = new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(),
                logFolder.getRoot().getAbsolutePath(),1000);
        tangle.addPersistenceProvider(rocksDBPersistenceProvider);
        tangle.init();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
    }

    @After
    public void tearDown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
    }

    @Test
    public void save() throws Exception {
    }

    @Test
    public void getKeysStartingWithValue() throws Exception {
        byte[] trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        Set<Indexable> tag = tangle.keysStartingWith(Tag.class, Arrays.copyOf(transactionViewModel.getTagValue().bytes(), 15));
        Assert.assertNotEquals(tag.size(), 0);
    }

    @Test
    public void get() throws Exception {
    }

    public static byte[] getRandomTransactionTrits() {
        byte[] out = new byte[TransactionViewModel.TRINARY_SIZE];

        for(int i = 0; i < out.length; i++) {
            out[i] = (byte) (seed.nextInt(3) - 1);
        }

        return out;
    }

}
