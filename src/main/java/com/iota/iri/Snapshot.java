package com.iota.iri;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

import com.iota.iri.model.HashFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;


public class Snapshot {
    private static final Logger log = LoggerFactory.getLogger(Snapshot.class);
    private static final String SNAPSHOT_PUBKEY = "TTXJUGKTNPOOEXSTQVVACENJOQUROXYKDRCVK9LHUXILCLABLGJTIPNF9REWHOIMEUKWQLUOKD9CZUYAC";
    private static final int SNAPSHOT_PUBKEY_DEPTH = 6;
    private static final int SNAPSHOT_INDEX = 9;
    private static Snapshot initialSnapshot;
    protected final Map<Hash, Long> state;
    private int index;

    public final ReadWriteLock rwlock = new ReentrantReadWriteLock();


    public static Snapshot init(SnapshotConfig config) throws IOException {
        //This is not thread-safe (and it is ok)
        if (initialSnapshot == null) {
            String snapshotFile = config.getSnapshotFile();
            if (!config.isTestnet() && !SignedFiles.isFileSignatureValid(snapshotFile, config.getSnapshotSignatureFile(),
                    SNAPSHOT_PUBKEY, SNAPSHOT_PUBKEY_DEPTH, SNAPSHOT_INDEX)) {
                throw new IllegalStateException("Snapshot signature failed.");
            }
            Map<Hash, Long> initialState = initInitialState(snapshotFile);
            initialSnapshot = new Snapshot(initialState, 0);
            checkStateHasCorrectSupply(initialState);
            checkInitialSnapshotIsConsistent(initialState);
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
        BufferedInputStream bufferedInputStream;
        try (InputStream snapshotStream = getSnapshotStream(snapshotFile)) {
            bufferedInputStream = new BufferedInputStream(snapshotStream);
            BufferedReader reader = new BufferedReader(new InputStreamReader(bufferedInputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";", 2);
                if (parts.length >= 2) {
                    String key = parts[0];
                    String value = parts[1];
                    state.put(HashFactory.ADDRESS.create(key), Long.valueOf(value));
                }
            }
        } catch (IOException e) {
            //syso is left until logback is fixed
            System.out.println("Failed to load snapshot.");
            log.error("Failed to load snapshot.", e);
            System.exit(-1);
        }
        return state;
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
            throw new IllegalStateException("Diff is not consistent.");
        }
        rwlock.writeLock().lock();
        patch.forEach((key, value) -> {
            if (state.computeIfPresent(key, (hash, aLong) -> value + aLong) == null) {
                state.putIfAbsent(key, value);
            }
        });
        index = newIndex;
        rwlock.writeLock().unlock();
    }

    public static boolean isConsistent(Map<Hash, Long> state) {
        final Iterator<Map.Entry<Hash, Long>> stateIterator = state.entrySet().iterator();
        while (stateIterator.hasNext()) {

            final Map.Entry<Hash, Long> entry = stateIterator.next();
            if (entry.getValue() <= 0) {

                if (entry.getValue() < 0) {
                    log.info("Skipping negative value for address: {}: {}", entry.getKey(), entry.getValue());
                    return false;
                }

                stateIterator.remove();
            }
            //////////// --Coo only--
                /*
                 * if (entry.getValue() > 0) {
                 *
                 * System.out.ln("initialState.put(new Hash(\"" + entry.getKey()
                 * + "\"), " + entry.getValue() + "L);"); }
                 */
            ////////////
        }
        return true;
    }
}
