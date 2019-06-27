package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.KeyValue;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.Set;

/**
 * For KV store
 */
public class KeyViewModel implements HashesViewModel {
    private KeyValue self;
    private Indexable hash;

    public KeyViewModel(Hash hash) {
        this.hash = hash;
    }

    private KeyViewModel(KeyValue hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new KeyValue(): hashes;
        this.hash = hash;
    }

    public static KeyViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new KeyViewModel((KeyValue) tangle.load(KeyValue.class, hash), hash);
    }

    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, hash);
    }

    public int size() {
        return self.set.size();
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
    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(KeyValue.class,hash);
    }

    public static KeyViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(KeyValue.class, Hash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new KeyViewModel((KeyValue) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    public KeyViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(KeyValue.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new KeyViewModel((KeyValue) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
