package com.iota.iri;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;


public class Snapshot {
    private static final Logger log = LoggerFactory.getLogger(Snapshot.class);
    private static String SNAPSHOT_PUBKEY = "ETSYRXPKSCTJAZIJZDVJTQOILVEPHGV9PHPFLJVUFQRPXGNWPDBAKHCWPPEXPCZDIGPJDQGHVIQHQYQDW";
    private static int SNAPSHOT_PUBKEY_DEPTH = 6;
    private static int SNAPSHOT_INDEX = 0;

    public static final Map<Hash, Long> initialState = new HashMap<>();
    public static final Snapshot initialSnapshot;

    static {

        InputStream in = Snapshot.class.getResourceAsStream("/Snapshot.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        Sponge curl = SpongeFactory.create(SpongeFactory.Mode.KERL);
        int[] trit_value;
        int[] trits = new int[Curl.HASH_LENGTH*3];
        try {
            while((line = reader.readLine()) != null) {
                trit_value = Converter.trits(Converter.asciiToTrytes(line));
                System.arraycopy(trit_value, 0, trits, 0, trit_value.length);
                curl.absorb(trits, 0, trits.length);
                Arrays.fill(trits, 0);
                String[] parts = line.split(":", 2);
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
                int[] digests = new int[0];
                int[] bundle = ISS.normalizedBundle(trits);
                int[] root = null;
                int i;
                in = Snapshot.class.getResourceAsStream("/Snapshot.sig");
                reader = new BufferedReader(new InputStreamReader(in));
                for(i = 0; i < 3 && (line = reader.readLine()) != null; i++) {
                    digests = ArrayUtils.addAll(digests, ISS.digest(SpongeFactory.Mode.KERL, Arrays.copyOfRange(bundle, i*ISS.NORMALIZED_FRAGMENT_LENGTH, (i+1)*ISS.NORMALIZED_FRAGMENT_LENGTH), Converter.trits(line)));
                }
                if((line = reader.readLine()) != null) {
                    root = ISS.getMerkleRoot(SpongeFactory.Mode.CURLP81, ISS.address(SpongeFactory.Mode.KERL, digests), Converter.trits(line), 0, SNAPSHOT_INDEX, SNAPSHOT_PUBKEY_DEPTH);
                } else {
                    root = ISS.address(SpongeFactory.Mode.KERL, digests);
                }
                if(!Arrays.equals(Converter.trits(SNAPSHOT_PUBKEY), root)) {
                    throw new RuntimeException("Snapshot signature failed.");
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to load snapshot.");
            System.exit(-1);
        }

        initialSnapshot = new Snapshot(initialState, 0);
        if(!initialSnapshot.isConsistent()) {
            System.out.println("Initial Snapshot inconsistent.");
            System.exit(-1);
        }
    }

    public static final Object latestSnapshotSyncObject = new Object();
    private final Map<Hash, Long> state;
    private int index;

    public int index() {
        return index;
    }

    public Snapshot(Snapshot snapshot) {
        state = new HashMap<>(snapshot.state);
        this.index = snapshot.index;
    }

    private Snapshot(Map<Hash, Long> initialState, int index) {
        state = new HashMap<>(initialState);
        this.index = index;
    }

    public Map<Hash, Long> getState() {
        return state;
    }

    public Map<Hash, Long> diff(Map<Hash, Long> newState) {
        return newState.entrySet().parallelStream()
                .map(hashLongEntry ->
                        new HashMap.SimpleEntry<>(hashLongEntry.getKey(),
                                hashLongEntry.getValue() -
                                        (state.containsKey(hashLongEntry.getKey()) ?
                                                state.get(hashLongEntry.getKey()): 0) ))
                .filter(e -> e.getValue() != 0L)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Snapshot patch(Map<Hash, Long> diff, int index) {
        Map<Hash, Long> patchedState = state.entrySet().parallelStream()
                .map( hashLongEntry ->
                        new HashMap.SimpleEntry<>(hashLongEntry.getKey(),
                                hashLongEntry.getValue() +
                                        (diff.containsKey(hashLongEntry.getKey()) ?
                                         diff.get(hashLongEntry.getKey()) : 0)) )
                .filter(e -> e.getValue() != 0L)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        diff.entrySet().stream()
                .filter(e -> e.getValue() > 0L)
                .forEach(e -> patchedState.putIfAbsent(e.getKey(), e.getValue()));
        return new Snapshot(patchedState, index);
    }

    void merge(Snapshot snapshot) {
        state.clear();
        state.putAll(snapshot.state);
        index = snapshot.index;
    }

    boolean isConsistent() {
        long stateValue = state.values().stream().reduce(Math::addExact).orElse(Long.MAX_VALUE);
        if(stateValue != TransactionViewModel.SUPPLY) {
            long difference = TransactionViewModel.SUPPLY - stateValue;
            log.info("Transaction resolves to incorrect ledger balance: {}", difference);
            return false;
        }
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
