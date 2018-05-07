package com.iota.iri;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;


public final class Snapshot {

    private static final Logger log = LoggerFactory.getLogger(Snapshot.class);

    public static final String SNAPSHOT_PUBKEY = "TTXJUGKTNPOOEXSTQVVACENJOQUROXYKDRCVK9LHUXILCLABLGJTIPNF9REWHOIMEUKWQLUOKD9CZUYAC";
    public static final int SNAPSHOT_PUBKEY_DEPTH = 6;
    private static final int SNAPSHOT_INDEX = 4;
    public static final int SPENT_ADDRESSES_INDEX = 5;

    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();
    private final boolean immutable;
    private final Map<Hash, Long> state;
    private int index;

    /**
     * This returns an unmodifiable instance. Modifiable copies of the
     * returned {@link Snapshot} can be made using {@link #copySnapshot()}
     *
     * @param snapshotPath    the snapshot file
     * @param snapshotSigPath the snapshot signature file
     * @param testnet         true if using the testnet
     * @return {@link Snapshot} An unmodifiable Snapshot if read and verified.
     * @throws IOException if snapshot file has cannot be read.
     */
    public static Snapshot init(String snapshotPath, String snapshotSigPath, boolean testnet) throws IOException {
        if (!testnet && !SignedFiles.isFileSignatureValid(snapshotPath, snapshotSigPath, SNAPSHOT_PUBKEY,
            SNAPSHOT_PUBKEY_DEPTH, SNAPSHOT_INDEX)) {
            throw new IllegalArgumentException("Provided Snapshot data signature failed.");
        }
        Map<Hash, Long> state = initInitialState(snapshotPath);
        checkStateHasCorrectSupply(state);
        if (!isConsistent(state)) {
            throw new IllegalArgumentException("Provided Snapshot data inconsistent.");
        }
        state.entrySet().removeIf(entry -> entry.getValue() == 0); // PRUNE ZEROs
        return new Snapshot(state, 0, false);
    }

    private static void checkStateHasCorrectSupply(Map<Hash, Long> initialState) {
        long stateValue = initialState.values().stream().reduce(Math::addExact).orElse(Long.MAX_VALUE);
        if (stateValue != TransactionViewModel.SUPPLY) {
            throw new IllegalArgumentException("Provided initial state resolves to incorrect ledger balance: " +
                "" + (TransactionViewModel.SUPPLY - stateValue));
        }
    }

    private static Map<Hash, Long> initInitialState(String snapshotFile) throws IOException {
        try (InputStream inputStream = Snapshot.class.getResourceAsStream(snapshotFile);
             BufferedReader reader = new BufferedReader((inputStream == null)
                 ? new FileReader(snapshotFile) : new InputStreamReader(inputStream))) {

            Map<Hash, Long> state = new HashMap<>();

            reader.lines().forEach(line -> {
                int index = line.indexOf(';');
                if (index != -1 && index != line.length() - 1) {
                    String key = line.substring(0, index);
                    String value = line.substring(index + 1);
                    state.put(new Hash(key), Long.valueOf(value));
                }
            });
            return state;
        }
    }

    private Snapshot(Map<Hash, Long> state, int index, boolean immutable) {
        this.immutable = immutable;
        this.state = immutable ? Collections.unmodifiableMap(new HashMap<>(state)) : new HashMap<>(state);
        this.index = index;
    }

    Map<Hash, Long> copyState() { // VISIBLE FOR TESTING ONLY
        return withReadLockSupply(() -> new HashMap<>(state));
    }

    public int index() {
        return withReadLockSupply(() -> index);
    }

    /**
     * @return a modifiable copy of this.
     */
    public Snapshot copySnapshot() {
        return withReadLockSupply(() -> new Snapshot(state, index, false));
    }

    public Long getBalance(Hash hash) {
        return withReadLockSupply(() -> state.get(hash));
    }

    Map<Hash, Long> patchedDiff(Map<Hash, Long> diff) {
        return withReadLockSupply(() -> {
            Map<Hash, Long> map = new HashMap<>(diff.size());
            diff.forEach((key, value) -> {
                Long previous = state.getOrDefault(key, 0L);
                map.put(key, Math.addExact(previous, value));
            });
            return map;
        });
    }

    void apply(Map<Hash, Long> patch, int newIndex) {
        if (immutable) {
            throw new UnsupportedOperationException("This snapshot is marked immutable. Make a copy if you want the 'apply' function;");
        }
        rwlock.writeLock().lock();
        try {
            Long sum = patch.entrySet().stream().map(Map.Entry::getValue).reduce(Math::addExact).orElse(0L);
            if (sum != 0L) {
                throw new RuntimeException("Diff is not consistent. Sum is: " + sum);
            }

            patch.forEach((key, val) ->
                state.compute(key, (k, previous) -> Math.addExact(val, previous != null ? previous : 0L)));

            index = newIndex;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public boolean isConsistent() {
        return withReadLockSupply(() -> Snapshot.isConsistent(state));
    }

    static boolean isConsistent(Map<Hash, Long> state) {
        for (Map.Entry<Hash, Long> entry : state.entrySet()) {
            if (entry.getValue() < 0) {
                log.info("Skipping negative value for address: " + entry.getKey() + ": " + entry.getValue());
                return false;
            }
        }
        return true;
    }

    ///////////////////////////////////////////////////////////
    //
    // These methods below allow holders of Snapshot instances
    // to perform operations, optionally returning values, without
    // explicitly handling the locks. This ensures that the locks
    // are held privately and never exposed to deadlocks from
    // code that does not handle the locks properly.
    //
    // If no return value is required, then a null can be returned
    // and then disregarded.
    //
    ///////////////////////////////////////////////////////////
    public final <V> V withWriteLock(Callable<V> callable) throws Exception {
        rwlock.writeLock().lock();
        try {
            return callable.call();
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    // THIS IS NOT USED AT THE MOMENT BUT HAS BEEN ADDED FOR ANY FUTURE NEEDED USE AND
    // TO ENSURE THAT `withWriteLock` and `withReadLock` OFFER THE SAME USE OPTIONS.
    public final <V> V withWriteLockSupply(Supplier<V> supplier) {
        rwlock.writeLock().lock();
        try {
            return supplier.get();
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public final <V> V withReadLock(Callable<V> callable) throws Exception {
        rwlock.readLock().lock();
        try {
            return callable.call();
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public final <V> V withReadLockSupply(Supplier<V> supplier) {
        rwlock.readLock().lock();
        try {
            return supplier.get();
        } finally {
            rwlock.readLock().unlock();
        }
    }
}