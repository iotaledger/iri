package com.iota.iri;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public final class Snapshot {

    private static final Logger log = LoggerFactory.getLogger(Snapshot.class);

    public static String SNAPSHOT_PUBKEY = "TTXJUGKTNPOOEXSTQVVACENJOQUROXYKDRCVK9LHUXILCLABLGJTIPNF9REWHOIMEUKWQLUOKD9CZUYAC";
    public static int SNAPSHOT_PUBKEY_DEPTH = 6;
    public static int SNAPSHOT_INDEX = 2;
    public static int SPENT_ADDRESSES_INDEX = 3;
    private static Snapshot initialSnapshot;

    public final ReadWriteLock rwlock = new ReentrantReadWriteLock();
    private final Map<Hash, Long> state;
    private int index;


    /**
     * {@code initialSnapshot} will only be assigned if this method succeeds. In the event of failure,
     * the Java Virtual Machine will exit with -1.
     *
     * @param snapshotPath    the snapshot file
     * @param snapshotSigPath the snapshot signature file
     * @param testnet         true if using the testnet
     * @return {@code initialSnapshot} if the Snapshot is read, verified, and assigned.
     */
    public static Snapshot init(String snapshotPath, String snapshotSigPath, boolean testnet) {
        synchronized (Snapshot.class) {
            if (initialSnapshot != null) {
                return initialSnapshot;
            }
            try {
                if (!testnet && !SignedFiles.isFileSignatureValid(snapshotPath, snapshotSigPath, SNAPSHOT_PUBKEY,
                    SNAPSHOT_PUBKEY_DEPTH, SNAPSHOT_INDEX)) {
                    throw new IllegalStateException("Snapshot signature failed.");
                }
                Map<Hash, Long> initialState = initInitialState(snapshotPath);
                checkStateHasCorrectSupply(initialState);
                checkInitialSnapshotIsConsistent(initialState);
                // Prune zero values - to honour original intent of 'remove'
                // of ZERO values in original Snapshot.isConsistent(...)
                initialState.entrySet().removeIf(entry -> entry.getValue() == 0);

                return initialSnapshot = new Snapshot(initialState, 0);

            } catch (Exception e) {
                log.error("Quitting - Unable to verify snapshot: " + e, e);
                System.exit(-1);
                throw new IllegalStateException("unreachable");
            }
        }
    }

    private static void checkInitialSnapshotIsConsistent(Map<Hash, Long> initialState) {
        if (!isConsistent(initialState)) {
            throw new IllegalStateException("Initial Snapshot inconsistent.");
        }
    }

    private static void checkStateHasCorrectSupply(Map<Hash, Long> initialState) {
        long stateValue = initialState.values().stream().reduce(Math::addExact).orElse(Long.MAX_VALUE);
        if (stateValue != TransactionViewModel.SUPPLY) {
            throw new IllegalStateException("Transaction resolves to incorrect ledger balance: " + (TransactionViewModel.SUPPLY - stateValue));
        }
    }

    private static Map<Hash, Long> initInitialState(String snapshotFile) {

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
        } catch (IOException e) {
            //serr is left until logback is fixed
            System.err.println("Failed to load snapshot.");
            throw new UncheckedIOException("Failed to load snapshot", e);
        }
    }

    public Map<Hash, Long> getState() {
        return state;
    }

    public int index() {
        rwlock.readLock().lock();
        try {
            return index;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    private Snapshot(Map<Hash, Long> initialState, int index) {
        state = new HashMap<>(initialState);
        this.index = index;
    }

    public Snapshot copySnapshot() {
        rwlock.readLock().lock();
        try {
            return new Snapshot(state, index);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public Long getBalance(Hash hash) {
        rwlock.readLock().lock();
        try {
            return state.get(hash);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public Map<Hash, Long> patchedDiff(Map<Hash, Long> diff) {
        rwlock.readLock().lock();
        try {
            Map<Hash, Long> map = new HashMap<>(diff.size());
            diff.forEach((key, value) -> {
                Long previousValue = state.getOrDefault(key, 0L);
                map.put(key, Math.addExact(previousValue, value));
            });
            return map;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    void apply(Map<Hash, Long> patch, int newIndex) {
        if (!patch.entrySet().stream().map(Map.Entry::getValue).reduce(Math::addExact).orElse(0L).equals(0L)) {
            throw new RuntimeException("Diff is not consistent.");
        }
        rwlock.writeLock().lock();
        try {
            patch.forEach((key, value) -> {
                Long previousValue = state.getOrDefault(key, 0L);
                state.put(key, Math.addExact(value, previousValue));
            });
            index = newIndex;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public static boolean isConsistent(Map<Hash, Long> state) {
        for (Map.Entry<Hash, Long> entry : state.entrySet()) {
            if (entry.getValue() < 0) {
                log.info("Skipping negative value for address: " + entry.getKey() + ": " + entry.getValue());
                return false;
            }
        }
        return true;
    }
}