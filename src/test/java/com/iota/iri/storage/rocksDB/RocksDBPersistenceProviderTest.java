package com.iota.iri.storage.rocksDB;

import com.iota.iri.model.*;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Pair;
import org.junit.*;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/4/17 for iri.
 */
public class RocksDBPersistenceProviderTest {

    private static final RocksDBPersistenceProvider rocksDBPersistenceProvider = new RocksDBPersistenceProvider(
            "mainnetdb", "mainnet.log", 100000);

    private final List<Pair<Indexable, Persistable>> dbEntries;

    public RocksDBPersistenceProviderTest() {
        dbEntries = createEntries();
    }

    @BeforeClass
    public static void initializeDb() throws Exception {
        rocksDBPersistenceProvider.init();
    }

    @AfterClass
    public static void shutDownDb() {
        rocksDBPersistenceProvider.shutdown();
    }

    @Before
    public void populateDb() throws Exception {
        rocksDBPersistenceProvider.saveBatch(dbEntries);
    }

    @After
    public void clearDb() throws Exception {
        rocksDBPersistenceProvider.clear(Address.class);
    }

    private List<Pair<Indexable, Persistable>> createEntries() {
        List<Pair<Indexable, Persistable>> entries = new ArrayList<>(3);
        Hashes hashes1 = TestModelUtils.createAddresses(20);
        Hashes hashes2 = TestModelUtils.createAddresses(40);
        Address hashes3 = TestModelUtils.createAddresses(30);
        entries.add(new Pair<>(new Hash(new byte[]{1},0, 8), hashes1));
        entries.add(new Pair<>(new Hash(new byte[]{2},0, 8), hashes2));
        entries.add(new Pair<>(new Hash(new byte[]{3},0, 8), hashes3));


        return entries;
    }


    @Test
    public void testSaveAndGetSplittable() throws Exception {
        Address hashes = TestModelUtils.createAddresses(RocksDBPersistenceProvider.BYTE_LENGTH_SPLIT * 5);
        byte[] ind = new byte[]{4};
        Indexable index = new Hash(ind, 0, ind.length);
        assertTrue("save 1 failed",
                rocksDBPersistenceProvider.save(hashes, index));

        Address hashesFromDb = (Address) rocksDBPersistenceProvider.get(Address.class, index);
        assertEquals("The saved Hashes is different than the fetched Hashes", hashes, hashesFromDb);

        hashes = TestModelUtils.createAddresses(RocksDBPersistenceProvider.BYTE_LENGTH_SPLIT *6);
        assertTrue("save 2 failed",
                rocksDBPersistenceProvider.save(hashes, index));
        hashesFromDb = (Address) rocksDBPersistenceProvider.get(Address.class, index);
        assertEquals("The saved Hashes is different than the fetched Hashes, old data",
                hashes, hashesFromDb);

    }

    @Test
    public void testMergeAndGetSplittable() throws Exception {
        Address hashes = TestModelUtils.createAddresses(5 * RocksDBPersistenceProvider.BYTE_LENGTH_SPLIT);
        byte[] ind = new byte[]{3};
        Indexable index = new Hash(ind, 0, 8);
        rocksDBPersistenceProvider.merge(hashes, index);

        Address hashesFromDb = (Address) rocksDBPersistenceProvider.get(Address.class, index);

        Address hashes3 = (Address) dbEntries.get(2).hi;
        List<Hash> expected = new ArrayList<>(hashes3.set.size() + hashes.set.size());
        expected.addAll(hashes3.set);
        expected.addAll(hashes.set);

        List<Hash> actual = new ArrayList<>(hashesFromDb.set.size());
        actual.addAll(hashesFromDb.set);

        assertEquals("The saved Hashes is different than the fetched Hashes", expected, actual);
    }

    @Test
    public void testSplitDelete() throws Exception {
        Indexable index = dbEntries.get(0).low;
        rocksDBPersistenceProvider.delete(Address.class, index);
        Address address = (Address) rocksDBPersistenceProvider.get(Address.class, index);
        assertTrue("entry was not deleted", address == null || address.set.isEmpty());

        Persistable hashes = TestModelUtils.createAddresses(RocksDBPersistenceProvider.BYTE_LENGTH_SPLIT * 4);
        byte[] ind = new byte[]{4};
        index = new Hash(ind, 0, ind.length);
        assertTrue("save failed",
                rocksDBPersistenceProvider.save(hashes, index));

        rocksDBPersistenceProvider.delete(Address.class, index, 2);

        Address expectedHashes = TestModelUtils.createAddresses(RocksDBPersistenceProvider.BYTE_LENGTH_SPLIT * 2);
        Persistable hashesFromDb = rocksDBPersistenceProvider.get(Address.class, index);
        assertEquals("The saved Hashes is different than the fetched Hashes", expectedHashes, hashesFromDb);
    }
}