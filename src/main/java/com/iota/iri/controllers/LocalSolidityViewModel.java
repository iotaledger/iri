package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.LocalSolidityHashes;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by paul on 5/6/17.
 */
public class LocalSolidityViewModel {
    private LocalSolidityHashes self;
    private Indexable hash;

    public LocalSolidityViewModel(Indexable index) {
        self = new LocalSolidityHashes();
        this.hash = index;
    }

    private LocalSolidityViewModel(LocalSolidityHashes hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new LocalSolidityHashes(): hashes;
        this.hash = hash;
    }

    public static LocalSolidityViewModel load(Indexable hash) throws Exception {
        return new LocalSolidityViewModel((LocalSolidityHashes) Tangle.instance().load(LocalSolidityHashes.class, hash), hash);
    }

    public static Map.Entry<Indexable, Persistable> getEntry(Hash hash, Hash hashToMerge) throws Exception {
        LocalSolidityHashes hashes = new LocalSolidityHashes();
        hashes.set.add(hashToMerge);
        return new HashMap.SimpleEntry<>(hash, hashes);
    }

    /*
    public static boolean merge(Hash hash, Hash hashToMerge) throws Exception {
        Hashes hashes = new Hashes();
        hashes.set = new HashSet<>(Collections.singleton(hashToMerge));
        return Tangle.instance().merge(hashes, hash);
    }
    */

    public boolean store() throws Exception {
        return Tangle.instance().save(self, hash);
    }

    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    public Indexable getIndex() {
        return hash;
    }

    public Set<Hash> getHashes() {
        return self.set;
    }
}
