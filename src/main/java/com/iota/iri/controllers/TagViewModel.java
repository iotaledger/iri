package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.ObsoleteTag;
import com.iota.iri.model.persistables.Tag;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TagViewModel implements HashesViewModel {
    private Tag self;
    private Indexable hash;

    public TagViewModel(Hash hash) {
        this.hash = hash;
    }

    private TagViewModel(Tag hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Tag(): hashes;
        this.hash = hash;
    }

    private static TagViewModel load(Tangle tangle, Indexable hash, Class<? extends Tag> model) throws Exception {
        return new TagViewModel((Tag) tangle.load(model, hash), hash);
    }

    public static TagViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return load(tangle, hash, Tag.class);
    }

    public static TagViewModel loadObsolete(Tangle tangle, Indexable hash) throws Exception {
        return load(tangle, hash, ObsoleteTag.class);
    }

    public static Map.Entry<Indexable, Persistable> getEntry(Hash hash, Hash hashToMerge) throws Exception {
        Tag hashes = new Tag();
        hashes.set.add(hashToMerge);
        return new HashMap.SimpleEntry<>(hash, hashes);
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
