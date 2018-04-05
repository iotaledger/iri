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
import java.util.stream.Collectors;


public class Snapshot {

    private static final Logger log = LoggerFactory.getLogger(Snapshot.class);

    public static String SNAPSHOT_PUBKEY = "TTXJUGKTNPOOEXSTQVVACENJOQUROXYKDRCVK9LHUXILCLABLGJTIPNF9REWHOIMEUKWQLUOKD9CZUYAC";
    public static int SNAPSHOT_PUBKEY_DEPTH = 6;
    public static int SNAPSHOT_INDEX = 2;
    public static int SPENT_ADDRESSES_INDEX = 3;
    private static Snapshot initialSnapshot;

    public final ReadWriteLock rwlock = new ReentrantReadWriteLock();
    protected final Map<Hash, Long> state;
    private int index;

    public static Snapshot init(String snapshotPath, String snapshotSigPath, boolean testnet) {
        //This is not thread-safe (and it is ok)
        if (initialSnapshot == null) {
            if (!testnet && !SignedFiles.isFileSignatureValid(snapshotPath, snapshotSigPath, SNAPSHOT_PUBKEY,
                SNAPSHOT_PUBKEY_DEPTH, SNAPSHOT_INDEX)) {
                throw new RuntimeException("Snapshot signature failed.");
            }

            Map<Hash, Long> initialState = initInitialState(snapshotPath);
            initialSnapshot = new Snapshot(initialState, 0);
            checkStateHasCorrectSupply(initialState);
            checkInitialSnapshotIsConsistent(initialState);

            // prune zero values to honour original intent of 'remove' of ZERO values in Snapshot.isConsistent(...)
            initialState.entrySet().removeIf(entry -> entry.getValue() == 0);
        }
        return initialSnapshot;
    }

    private static InputStream getSnapshotStream(String snapshotPath) throws FileNotFoundException {
        InputStream inputStream = Snapshot.class.getResourceAsStream(snapshotPath);
        //if resource doesn't exist, read from file system
        if (inputStream == null) {
            inputStream = new FileInputStream(snapshotPath);
        }

        return inputStream;
    }

    private static void checkInitialSnapshotIsConsistent(Map<Hash, Long> initialState) {
        if (!isConsistent(initialState)) {
            log.error("Initial Snapshot inconsistent.");
            System.exit(-1);
        }
    }

    private static void checkStateHasCorrectSupply(Map<Hash, Long> initialState) {
        long stateValue = initialState.values().stream().reduce(Math::addExact).orElse(Long.MAX_VALUE);
        if (stateValue != TransactionViewModel.SUPPLY) {
            log.error("Transaction resolves to incorrect ledger balance: {}", TransactionViewModel.SUPPLY - stateValue);
            System.exit(-1);
        }
    }

    private static Map<Hash, Long> initInitialState(String snapshotFile) {
        Map<Hash, Long> state = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(getSnapshotStream(snapshotFile)))) {

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
            log.error("Failed to load snapshot.", e);
            throw new UncheckedIOException("Failed to load snapshot", e);
        }
    }

    public int index() {
        int i;
        rwlock.readLock().lock();
        i = index;
        rwlock.readLock().unlock();
        return i;
    }

    private Snapshot(Map<Hash, Long> initialState, int index) {
        state = new HashMap<>(initialState);
        this.index = index;
    }

    public Snapshot clone() {
        return new Snapshot(state, index);
    }

    public Long getBalance(Hash hash) {
        Long l;
        rwlock.readLock().lock();
        l = state.get(hash);
        rwlock.readLock().unlock();
        return l;
    }

    public Map<Hash, Long> patchedDiff(Map<Hash, Long> diff) {
        Map<Hash, Long> patch;
        rwlock.readLock().lock();
        patch = diff.entrySet().stream().map(hashLongEntry ->
            new HashMap.SimpleEntry<>(hashLongEntry.getKey(), state.getOrDefault(hashLongEntry.getKey(), 0L) + hashLongEntry.getValue())
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        rwlock.readLock().unlock();
        return patch;
    }

    void apply(Map<Hash, Long> patch, int newIndex) {
        if (!patch.entrySet().stream().map(Map.Entry::getValue).reduce(Math::addExact).orElse(0L).equals(0L)) {
            throw new RuntimeException("Diff is not consistent.");
        }
        rwlock.writeLock().lock();
        patch.entrySet().forEach(hashLongEntry -> {
            if (state.computeIfPresent(hashLongEntry.getKey(), (hash, aLong) -> hashLongEntry.getValue() + aLong) == null) {
                state.putIfAbsent(hashLongEntry.getKey(), hashLongEntry.getValue());
            }
        });
        index = newIndex;
        rwlock.writeLock().unlock();
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