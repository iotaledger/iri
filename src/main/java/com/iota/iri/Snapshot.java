package com.iota.iri;
import com.iota.iri.model.Hash;
import com.iota.iri.controllers.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;


public class Snapshot {
    private static final Logger log = LoggerFactory.getLogger(Snapshot.class);
    public static String SNAPSHOT_PUBKEY = "TTXJUGKTNPOOEXSTQVVACENJOQUROXYKDRCVK9LHUXILCLABLGJTIPNF9REWHOIMEUKWQLUOKD9CZUYAC";
    public static int SNAPSHOT_PUBKEY_DEPTH = 6;
    public static int SNAPSHOT_INDEX = 2;
    public static int SPENT_ADDRESSES_INDEX = 3;

    public static final Map<Hash, Long> initialState = new HashMap<>();
    public static final Snapshot initialSnapshot;
    public final ReadWriteLock rwlock = new ReentrantReadWriteLock();

    static {

        if (!SignedFiles.isFileSignatureValid("/Snapshot.txt", "/Snapshot.sig", SNAPSHOT_PUBKEY, SNAPSHOT_PUBKEY_DEPTH, SNAPSHOT_INDEX)) {
            throw new RuntimeException("Snapshot signature failed.");
        }

        InputStream in = Snapshot.class.getResourceAsStream("/Snapshot.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        try {
            while((line = reader.readLine()) != null) {
                String[] parts = line.split(";", 2);
                if (parts.length >= 2)
                {
                    String key = parts[0];
                    String value = parts[1];
                    initialState.put(new Hash(key), Long.valueOf(value));
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to load snapshot.");
            System.exit(-1);
        }

        initialSnapshot = new Snapshot(initialState, 0);
        long stateValue = initialState.values().stream().reduce(Math::addExact).orElse(Long.MAX_VALUE);
        if(stateValue != TransactionViewModel.SUPPLY) {
            log.error("Transaction resolves to incorrect ledger balance: {}", TransactionViewModel.SUPPLY - stateValue);
            System.exit(-1);
        }

        if(!isConsistent(initialState)) {
            System.out.println("Initial Snapshot inconsistent.");
            System.exit(-1);
        }
    }

    protected final Map<Hash, Long> state;
    private int index;

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
        patch.entrySet().stream().forEach(hashLongEntry -> {
            if (state.computeIfPresent(hashLongEntry.getKey(), (hash, aLong) -> hashLongEntry.getValue() + aLong) == null) {
                state.putIfAbsent(hashLongEntry.getKey(), hashLongEntry.getValue());
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
                    log.info("Skipping negative value for address: " + entry.getKey() + ": " + entry.getValue());
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
