package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Address;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.Set;

/**
 * Acts as a controller interface for <tt>Address</tt> objects. These controllers are used within a
 * <tt>TransactionViewModel</tt> to manipulate an Address object.
 */
public class AddressViewModel implements HashesViewModel {
    private Address self;
    private Indexable hash;

    /**
     * Constructor for an <tt>Address</tt> controller from a hash identifier.
     * @param hash The Hash identifier that the controller will be created from.
     */
    public AddressViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
     * Constructor for an <tt>Address</tt> controller from an existing Address set. If the set is empty, an empty
     * set is created.
     *
     * @param hashes The <tt>Address</tt> set that the controller will be created from
     * @param hash The hash identifier that acts as a reference for the <tt>Address</tt> set
     */
    private AddressViewModel(Address hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Address(): hashes;
        this.hash = hash;
    }

    /**
     * Creates a new controller for an <tt>Address</tt> set that has been found in the database using a
     * hash identifier.
     *
     * @param tangle The tangle reference for the database to find the <tt>Address</tt> set in
     * @param hash The hash identifier for the <tt>Address</tt> set that needs to be found
     * @return The <tt>Address</tt> controller generated
     * @throws Exception Thrown if the database cannot load an <tt>Address</tt> set from the reference hash
     */
    public static AddressViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new AddressViewModel((Address) tangle.load(Address.class, hash), hash);
    }

    /**
     * Stores the <tt>Address</tt> object indexed by its hash identifier in the database.
     *
     * @param tangle The tangle reference for the database
     * @return True if the object was saved correctly, False if not
     * @throws Exception Thrown if the address object or index are null
     */
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, hash);
    }

    /**@return The size of the <tt>Address</tt> set stored in the controller*/
    public int size() {
        return self.set.size();
    }

    /**
     * Adds the given <tt>Address</tt> hash identifier to the stored <tt>Address</tt> set
     *
     * @param theHash The hash identifier to be added to the set
     * @return True if the hash is stored correctly, False if not
     */
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    /**@return The index hash identifier of the object*/
    public Indexable getIndex() {
        return hash;
    }

    /**@return The <tt>Address</tt> set of the object*/
    public Set<Hash> getHashes() {
        return self.set;
    }

    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Address.class,hash);
    }

    /**
     * Fetches the first persistable <tt>Address</tt> object from the database and generates a new controller
     * from it. If no objects exist in the database, it will return a null pair.
     *
     * @param tangle the tangle reference for the database
     * @return The new controller
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static AddressViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Address.class, Hash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new AddressViewModel((Address) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    /**
     * Fetches the next indexed persistable <tt>Address</tt> object from the database and generates a new
     * controller from it. If no objects exist in the database, it will return a null pair.
     *
     * @param tangle the tangle reference for the database
     * @return The new controller
     * @throws Exception Thrown if the database fails to return a next object
     */
    public AddressViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Address.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new AddressViewModel((Address) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
