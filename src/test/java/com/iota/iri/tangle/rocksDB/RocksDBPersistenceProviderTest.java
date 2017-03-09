package com.iota.iri.tangle.rocksDB;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.Flag;
import com.iota.iri.model.Transaction;
import com.iota.iri.tangle.IPersistenceProvider;
import com.iota.iri.tangle.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.service.TransactionViewModel;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/4/17 for iri.
 */
public class RocksDBPersistenceProviderTest {

    @BeforeClass
    public static void setUp() throws Exception {
        TemporaryFolder dbFolder = new TemporaryFolder();
        dbFolder.create();
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init(dbFolder.getRoot().getAbsolutePath());
    }

    @AfterClass
    public static void tearDown() throws Exception {
        Tangle.instance().shutdown();
    }

    @Test
    public void save() throws Exception {
        Random r = new Random();
        int[] trits = Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray(),
                hash = new int[Curl.HASH_LENGTH];
        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(trits);
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(hash);

        Tangle.instance().getPersistenceProviders().get(0).save(transaction);
    }

    @Test
    public void get() throws Exception {
        Random r = new Random();
        int[] trits = Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray(),
                hash = new int[Curl.HASH_LENGTH];
        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(trits);
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(hash);

        Tangle.instance().getPersistenceProviders().get(0).save(transaction);
        Transaction getTransaction = new Transaction();
        getTransaction = (Transaction) Tangle.instance().getPersistenceProviders().get(0).get(Transaction.class, transaction.hash);
        assertArrayEquals(getTransaction.hash, transaction.hash);
        assertArrayEquals(getTransaction.bytes, transaction.bytes);
    }

    @Test
    public void query() throws Exception {
        Random r = new Random();
        int[] trits = Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray(),
                hash = new int[Curl.HASH_LENGTH];
        Transaction transaction = new Transaction(), queryTransaction;
        transaction.bytes = Converter.bytes(trits);
        transaction.address.bytes = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(hash);

        Tangle.instance().getPersistenceProviders().get(0).save(transaction);
        queryTransaction = Arrays.stream(
                Arrays.stream(
                        Tangle.instance()
                                .query(Transaction.class, "address", transaction.address, TransactionViewModel.BUNDLE_SIZE)
                                .get())
                        .toArray(Transaction[]::new))
                .findFirst()
                .orElse(null);
        assertNotNull(queryTransaction);
        assertArrayEquals(queryTransaction.hash, transaction.hash);
        assertArrayEquals(queryTransaction.bytes, transaction.bytes);
        assertArrayEquals(queryTransaction.address.bytes, transaction.address.bytes);
    }

    @Test
    public void queryManyTest() throws Exception {
        Random r = new Random();
        byte[] bundleHash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        Transaction[] bundle, transactions;
        transactions = Arrays.stream(new Transaction[4]).map(t -> {
            Transaction transaction = new Transaction();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.bundle.hash = bundleHash.clone();
            transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
            return transaction;
        }).toArray(Transaction[]::new);
        transactions[0].hash = bundleHash.clone();

        for(Transaction transaction: transactions) {
            Tangle.instance().getPersistenceProviders().get(0).save(transaction);
        }
        Object[] queryOutput = Tangle.instance()
                .query(Transaction.class, "bundle", transactions[0].bundle, bundleHash.length)
                .get();
        bundle = Arrays.stream(queryOutput).toArray(Transaction[]::new);
        //bundle = Arrays.stream(Tangle.instance().getPersistenceProviders().get(0).queryMany(Transaction.class, "bundle", bundleHash, bundleHash.length)).toArray(Transaction[]::new);
        assertEquals(bundle.length, transactions.length);
    }

    @Test
    public void setTransientHandle() throws Exception {
        Object uuid = Tangle.instance().createTransientList(Flag.class);
        assertNotNull(UUID.class.equals(uuid.getClass()));
    }

    @Test
    public void dropTransientHandle() throws Exception {
        Object uuid = Tangle.instance().createTransientList(Flag.class);
        Tangle.instance().dropList(uuid);
        try {
            Tangle.instance().dropList(uuid);
            assertFalse("Oh no You Dinnit", true);
        } catch (Exception e) {
            assertTrue("Did not exist", true);
        }
    }

    @Test
    public void transientSave() throws Exception {
        Object uuid = Tangle.instance().createTransientList(Flag.class);
        Flag flag = new Flag();
        flag.hash = "SOMESTRINGOROTHER".getBytes();
        try {
            Tangle.instance().save(uuid, flag).get();
            assertTrue("Got this far", true);
        } catch (Exception e) {
            assertTrue("Failed to save", false);
        }

    }

    @Test
    public void maybeHas() throws Exception {
        Object uuid = Tangle.instance().createTransientList(Flag.class);
        Flag flag = new Flag();
        flag.hash = "SOMESTRINGOROTHER".getBytes();
        Tangle.instance().save(uuid, flag).get();
        boolean maybeHas = Tangle.instance().maybeHas(uuid, "9OBADBYTES".getBytes()).get();
        assertFalse("Should not contain junk", maybeHas);
        maybeHas = Tangle.instance().maybeHas(uuid, flag.hash).get();
        assertTrue("Should have it in DB", maybeHas);
    }

    @Test
    public void get1() throws Exception {
        Object uuid = Tangle.instance().createTransientList(Flag.class);
        Flag flag = new Flag();
        flag.hash = "SOMESTRINGOROTHER".getBytes();
        flag.status = false;
        Tangle.instance().save(uuid, flag).get();
        Flag output = (Flag)Tangle.instance().load(uuid, Flag.class, flag.hash).get();
        assertArrayEquals(output.hash, flag.hash);
        assertEquals(output.status, flag.status);
    }

    @Test
    public void deleteTransientObject() throws Exception {
        Object uuid = Tangle.instance().createTransientList(Flag.class);
        Flag flag = new Flag();
        flag.hash = "SOMESTRINGOROTHER".getBytes();
        flag.status = false;
        Tangle.instance().save(uuid, flag).get();
        assertTrue(Tangle.instance().maybeHas(uuid, flag.hash).get());
        Tangle.instance().delete(uuid, flag.hash).get();
        assertFalse(Tangle.instance().maybeHas(uuid, flag.hash).get());
    }
}