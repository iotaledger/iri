package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Approvee;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Acts as a controller interface for an {@link Approvee} set. These controllers are used within a
 * {@link TransactionViewModel} to manipulate an {@link Approvee} set.
 */
public class ApproveeViewModel implements HashesViewModel {
    private Approvee self;
    private Indexable hash;

    /**
     * Creates an empty <tt>Approvee</tt> controller. This controller is created using a given hash identifier.
     *
     * @param hash The hash identifier that the {@link ApproveeViewModel} will be referenced by
     */
    public ApproveeViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
     * Creates a new <tt>Approvee</tt> controller for an {@link Approvee} set. This controller is created using the
     * given hash identifier and a predefined {@link Approvee} set.
     *
     * @param hashes The {@link Approvee} set that the controller will be created from
     * @param hash The hash identifier that the {@link ApproveeViewModel} will be referenced by
     */
    private ApproveeViewModel(Approvee hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Approvee(): hashes;
        this.hash = hash;
    }

    /**
     * Creates a new <tt>Approvee</tt> controller for an {@link Approvee} set. This controller is created using the
     * given hash identifier.
     *
     * @param tangle The tangle reference for the database.
     * @param hash The hash identifier that the {@link Approvee} set will be created from to generate the controller
     * @return The <tt>Approvee</tt> controller
     * @throws Exception Thrown if there is an error generating the new controller.
     */
    public static ApproveeViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new ApproveeViewModel((Approvee) tangle.load(Approvee.class, hash), hash);
    }

    /**
     * Creates a new {@link Approvee} set and stores the second given hash identifier within it. Then a new entry
     * is created, indexing the newly created {@link Approvee} set to the first given hash identifier.
     *
     * @param hash The intended index hash identifier
     * @param hashToMerge The hash identifier that will be stored within the {@link Approvee} set
     * @return The newly created entry, mapping the {@link Approvee} set with the given index hash identifier
     * @throws Exception Thrown if there is an error adding the second hash to the {@link Approvee} set
     */
    public static Map.Entry<Indexable, Persistable> getEntry(Hash hash, Hash hashToMerge) throws Exception {
        Approvee hashes = new Approvee();
        hashes.set.add(hashToMerge);
        return new HashMap.SimpleEntry<>(hash, hashes);
    }

    /**
     * Attempts to store the {@link Approvee} set indexed by the hash identifier to the database.
     *
     * @param tangle The tangle reference for the database.
     * @return True if the set is stored correctly, False if not
     * @throws Exception Thrown if there is a failure storing the set to the database
     */
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, hash);
    }

    /**@return the integer size of the current {@link Approvee} set*/
    public int size() {
        return self.set.size();
    }

    /**
     * Adds a hash identifier to the {@link Approvee} set.
     *
     * @param theHash The hash identifier to be added
     * @return True if the hash is added correctly, False if not
     */
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    /**@return The hash identifier for the {@link Approvee} set*/
    public Indexable getIndex() {
        return hash;
    }

    /**@return The set of contained hash identifiers*/
    public Set<Hash> getHashes() {
        return self.set;
    }

    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Approvee.class,hash);
    }

    /**
     * Fetches the first persistable {@link Approvee} set from the database and generates a new controller
     * from it. If no objects exist in the database, it will return a null pair.
     *
     * @param tangle the tangle reference for the database
     * @return The new controller
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static ApproveeViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Approvee.class, Hash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new ApproveeViewModel((Approvee) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    /**
     * Fetches the next indexed persistable {@link Approvee} set from the database and generates a new
     * controller from it. If no objects exist in the database, it will return a null pair.
     *
     * @param tangle the tangle reference for the database
     * @return The new controller
     * @throws Exception Thrown if the database fails to return a next object
     */
    public ApproveeViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Approvee.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new ApproveeViewModel((Approvee) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
