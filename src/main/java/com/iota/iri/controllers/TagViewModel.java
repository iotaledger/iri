package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Tag;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.Set;
/**
 * Created by paul on 5/15/17.
 */
public class TagViewModel implements HashesViewModel {
    private Tag self;
    private Indexable hash;

    private TagViewModel(Tag hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Tag(): hashes;
        this.hash = hash;
    }

    public static TagViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new TagViewModel((Tag) tangle.load(Tag.class, hash), hash);
    }

    public int size() {
        return self.set.size();
    }

    public Set<Hash> getHashes() {
        return self.set;
    }
    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Tag.class,hash);
    }
    public static TagViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Tag.class, Hash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new TagViewModel((Tag) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    public TagViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Tag.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new TagViewModel((Tag) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
