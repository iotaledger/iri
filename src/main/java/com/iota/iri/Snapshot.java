package com.iota.iri;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.StateDiffViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;
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
    private static String SNAPSHOT_PUBKEY = "TTXJUGKTNPOOEXSTQVVACENJOQUROXYKDRCVK9LHUXILCLABLGJTIPNF9REWHOIMEUKWQLUOKD9CZUYAC";
    private static int SNAPSHOT_PUBKEY_DEPTH = 6;
    private static int SNAPSHOT_INDEX = 1;

    public static final Map<Hash, Long> initialState = new HashMap<>();
    public static final Snapshot initialSnapshot;
    public final ReadWriteLock rwlock = new ReentrantReadWriteLock();

    static {

        InputStream in = Snapshot.class.getResourceAsStream("/Snapshot.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        Sponge curl = SpongeFactory.create(SpongeFactory.Mode.KERL);
        int[] trits = new int[Curl.HASH_LENGTH*3];
        try {
            while((line = reader.readLine()) != null) {
                Converter.trits(Converter.asciiToTrytes(line), trits, 0);
                curl.absorb(trits, 0, trits.length);
                Arrays.fill(trits, 0);
                String[] parts = line.split(";", 2);
                if (parts.length >= 2)
                {
                    String key = parts[0];
                    String value = parts[1];
                    initialState.put(new Hash(key), Long.valueOf(value));
                }
            }
            { // Check snapshot signature
                trits = new int[Curl.HASH_LENGTH];
                curl.squeeze(trits, 0, Curl.HASH_LENGTH);
                SpongeFactory.Mode mode = SpongeFactory.Mode.CURLP81;
                int[] digests = new int[0];
                int[] bundle = ISS.normalizedBundle(trits);
                int[] root;
                int i;
                in = Snapshot.class.getResourceAsStream("/Snapshot.sig");
                reader = new BufferedReader(new InputStreamReader(in));
                for(i = 0; i < 3 && (line = reader.readLine()) != null; i++) {
                    int[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                    Converter.trits(line, lineTrits, 0);
                    digests = ArrayUtils.addAll(
                            digests,
                            ISS.digest(mode
                                    , Arrays.copyOfRange(bundle, i*ISS.NORMALIZED_FRAGMENT_LENGTH, (i+1)*ISS.NORMALIZED_FRAGMENT_LENGTH)
                                    , lineTrits));
                }
                if((line = reader.readLine()) != null) {
                    int[] lineTrits = Converter.allocateTritsForTrytes(line.length());
                    Converter.trits(line, lineTrits, 0);
                    root = ISS.getMerkleRoot(mode, ISS.address(mode, digests), lineTrits, 0, SNAPSHOT_INDEX, SNAPSHOT_PUBKEY_DEPTH);
                } else {
                    root = ISS.address(mode, digests);
                }

                int[] pubkeyTrits = Converter.allocateTritsForTrytes(SNAPSHOT_PUBKEY.length());
                Converter.trits(SNAPSHOT_PUBKEY, pubkeyTrits, 0);
                if(!Arrays.equals(pubkeyTrits, root)) {
                    throw new RuntimeException("Snapshot signature failed.");
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
