package com.iota.iri.controllers;

import com.iota.iri.model.Height;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.Set;

/**
 * Created by paul on 5/15/17.
 */
public class HeightViewModel implements HashesViewModel {
    private Height self;
    private Indexable height;

    public HeightViewModel(Hash height) {
        this.height = height;
    }

    private HeightViewModel(Height hashes, Indexable height) {
        self = hashes == null || hashes.set == null ? new Height(): hashes;
        this.height = height;
    }

    public static HeightViewModel load(Tangle tangle, Indexable height) throws Exception {
        return new HeightViewModel((Height) tangle.load(Height.class, height), height);
    }

    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, height);
    }

    public int size() {
        return self.set.size();
    }

    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    public Indexable getIndex() {
        return height;
    }

    public Set<Hash> getHashes() {
        return self.set;
    }
    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Height.class, height);
    }

    public static HeightViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Height.class, Hash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new HeightViewModel((Height) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    public HeightViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Height.class, height);
        if(bundlePair != null && bundlePair.hi != null) {
            return new HeightViewModel((Height) bundlePair.hi, bundlePair.low);
        }
        return null;
    }
}
