package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Bundle;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.HashMap;
import java.util.Map;
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
     * @return The {@link AddressViewModel} controller generated
     * @throws Exception Thrown if the database cannot load an {@link Bundle} set from the reference {@link Hash}
     */
    public static BundleViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new BundleViewModel((Bundle) tangle.load(Bundle.class, hash), hash);
    }

    /**
     * Creates a new {@link Bundle} set and stores the {@link Hash} set referenced by the second given {@link Hash}
     * identifier within it. Then a new entry is created, mapping the newly created {@link Bundle} set to the first
     * given {@link Hash} identifier.
     *
     * @param hash The intended index {@link Hash} identifier
     * @param hashToMerge The {@link Hash} identifier for the set that will be stored within the new {@link Bundle} set
     * @return The newly created entry, mapping the {@link Bundle} set with the given index {@link Hash} identifier
     * @throws Exception Thrown if there is an error adding the second hash to the {@link Bundle} object
     */
    public static Map.Entry<Indexable, Persistable> getEntry(Hash hash, Hash hashToMerge) throws Exception {
        Bundle hashes = new Bundle();
        hashes.set.add(hashToMerge);
        return new HashMap.SimpleEntry<>(hash, hashes);
    }

    /*
    public static boolean merge(Hash hash, Hash hashToMerge) throws Exception {
        Bundle hashes = new Bundle();
        hashes.set = new HashSet<>(Collections.singleton(hashToMerge));
        return Tangle.instance().merge(hashes, hash);
    }
    */

    /**
     * Stores the {@link Bundle} set indexed by its {@link Hash} identifier in the database.
     *
     * @param tangle The tangle reference for the database
     * @return True if the object was saved correctly, False if not
     * @throws Exception Thrown if the {@link Bundle} set or index {@link Hash} are null
     */
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, hash);
    }

    /**@return The size of the {@link Bundle} set referenced by the controller*/
    public int size() {
        return self.set.size();
    }

    /**
     * Adds the {@link Bundle} set referenced by the provided {@link Hash} to the stored {@link Bundle} set.
     *
     * @param theHash The {@link Hash} identifier to be added to the set
     * @return True if the {@link Bundle} set is added correctly, False if not
     */
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    /**@return The index {@link Hash} identifier of the {@link Bundle} set*/
    public Indexable getIndex() {
        return hash;
    }

    /**@return The {@link Bundle} set referenced by the controller*/
    public Set<Hash> getHashes() {
        return self.set;
    }

    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Bundle.class,hash);
    }

    /**
     * Fetches the first persistable {@link Bundle} set from the database and generates a new
     * {@link BundleViewModel} from it. If no {@link Bundle} sets exist in the database, it will return a null pair.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link BundleViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static BundleViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Bundle.class, Hash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new BundleViewModel((Bundle) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    /**
     * Fetches the next indexed persistable {@link Bundle} set from the database and generates a new
     * {@link BundleViewModel}from it. If no {@link Bundle} sets in the database, it will return a null pair.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link BundleViewModel}
     * @throws Exception Thrown if the database fails to return a next {@link Bundle} set
     */
    public BundleViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Bundle.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new BundleViewModel((Bundle) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
