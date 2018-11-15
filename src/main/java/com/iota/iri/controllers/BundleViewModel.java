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
 * Acts as a controller interface for {@link Bundle} objects. These controllers are used within a
 * {@link BundleViewModel} to manipulate an Address object.
 */
public class BundleViewModel implements HashesViewModel {
    private Bundle self;
    private Indexable hash;

    /**
     * Creates an empty <tt>Bundle</tt> controller. This controller is created using a given hash identifier.
     *
     * @param hash The hash identifier that the {@link BundleViewModel} will be referenced by
     */
    public BundleViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
     * Creates a new <tt>Bundle</tt> controller for an {@link Bundle} object. This controller is created using the
     * given hash identifier and a predefined {@link Bundle} object.
     *
     * @param hashes The {@link Bundle} object that the controller will be created from
     * @param hash The hash identifier that the {@link BundleViewModel} will be referenced by
     */
    private BundleViewModel(Bundle hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Bundle(): hashes;
        this.hash = hash;
    }

    /**
     * Creates a new <tt>Bundle</tt> controller for an {@link Bundle} object. This controller is created using the
     * given hash identifier.
     *
     * @param tangle The tangle reference for the database.
     * @param hash The hash identifier that the {@link Bundle} object will be created from to generate the controller
     * @return The <tt>Bundle</tt> controller
     * @throws Exception Thrown if there is an error generating the new controller.
     */
    public static BundleViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new BundleViewModel((Bundle) tangle.load(Bundle.class, hash), hash);
    }

    /**
     * Creates a new {@link Bundle} object and stores the second given hash identifier within it. Then a new entry
     * is created, indexing the newly created {@link Bundle} object to the first given hash identifier.
     *
     * @param hash The intended index hash identifier
     * @param hashToMerge The hash identifier that will be stored within the {@link Bundle} object
     * @return The newly created entry, mapping the {@link Bundle} object with the given index hash identifier
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
     * Attempts to store the <tt>Bundle</tt> hash object indexed by the hash identifier to the database.
     *
     * @param tangle The tangle reference for the database.
     * @return True if the set is stored correctly, False if not
     * @throws Exception Thrown if there is a failure storing the set to the database
     */
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, hash);
    }

    /**@return the integer size of the current {@link Bundle} object*/
    public int size() {
        return self.set.size();
    }

    /**
     * Adds a hash identifier to the {@link Bundle} object.
     *
     * @param theHash The hash identifier to be added
     * @return True if the hash is added correctly, False if not
     */
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    /**@return The hash identifier for the {@link Bundle} object*/
    public Indexable getIndex() {
        return hash;
    }

    /**@return The set of contained {@link Bundle} object*/
    public Set<Hash> getHashes() {
        return self.set;
    }

    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Bundle.class,hash);
    }

    /**
     * Fetches the first persistable {@link Bundle} object from the database and generates a new controller
     * from it. If no objects exist in the database, it will return a null pair.
     *
     * @param tangle the tangle reference for the database
     * @return The new controller
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
     * Fetches the next indexed persistable {@link Bundle} object from the database and generates a new
     * controller from it. If no objects exist in the database, it will return a null pair.
     *
     * @param tangle the tangle reference for the database
     * @return The new controller
     * @throws Exception Thrown if the database fails to return a next object
     */
    public BundleViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Bundle.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new BundleViewModel((Bundle) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
