package com.iota.iri.controllers;

import com.iota.iri.model.BundleHash;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Bundle;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.Set;

/**
 * Acts as a controller interface for a {@link Bundle} set. This controller is used within a
 * {@link TransactionViewModel} to manipulate a {@link Bundle} set.
 */
public class BundleViewModel implements HashesViewModel {
    private Bundle self;
    private Indexable hash;

    /**
     * Constructor for a {@link Bundle} set controller from a {@link Hash} identifier.
     * @param hash The {@link Hash} identifier that the controller will be created for.
     */
    public BundleViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
     * Constructor for a {@link Bundle} set controller from an existing {@link Bundle} set. If the set is empty, a new
     * {@link Bundle} set is created.
     *
     * @param hashes The {@link Bundle} set that the controller will be created from
     * @param hash The {@link Hash} identifier that acts as a reference for the {@link Bundle} set
     */
    private BundleViewModel(Bundle hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Bundle(): hashes;
        this.hash = hash;
    }

    /**
     * Creates a new {@link Bundle} set controller. This controller is created by extracting the {@link Bundle} set
     * from the database using the provided {@link Hash} identifier.
     *
     * @param tangle The tangle reference for the database to find the {@link Bundle} set in
     * @param hash The hash identifier for the {@link Bundle} set that needs to be found
     * @return The {@link BundleViewModel} controller generated
     * @throws Exception Thrown if the database cannot load an {@link Bundle} set from the reference {@link Hash}
     */
    public static BundleViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new BundleViewModel((Bundle) tangle.load(Bundle.class, hash), hash);
    }

    /**
     * Fetches the first persistable {@link Bundle} set from the database and generates a new
     * {@link BundleViewModel} from it. If no {@link Bundle} sets exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link BundleViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static BundleViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Bundle.class, BundleHash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new BundleViewModel((Bundle) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, hash);
    }

    @Override
    public int size() {
        return self.set.size();
    }

    @Override
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    @Override
    public Indexable getIndex() {
        return hash;
    }

    @Override
    public Set<Hash> getHashes() {
        return self.set;
    }

    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Bundle.class,hash);
    }

    @Override
    public BundleViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Bundle.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new BundleViewModel((Bundle) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
