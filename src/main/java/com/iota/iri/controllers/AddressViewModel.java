package com.iota.iri.controllers;

import com.iota.iri.model.Address;
import com.iota.iri.model.Bundle;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by paul on 5/15/17.
 */
public class AddressViewModel implements HashesViewModel {
    private Address self;
    private Indexable hash;

    public AddressViewModel(Hash hash) {
        this.hash = hash;
    }

    private AddressViewModel(Address hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Address(): hashes;
        this.hash = hash;
    }

    public static AddressViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new AddressViewModel((Address) tangle.load(Address.class, hash), hash);
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
        tangle.delete(Address.class,hash);
    }
}
