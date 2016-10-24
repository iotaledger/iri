package iri;

import java.util.*;

public class Snapshot {

    public static final Map<Hash, Long> initialState = new HashMap<>();

    static {

        initialState.put(Hash.NULL_HASH, Transaction.SUPPLY);
    }
}
