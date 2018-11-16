package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Address;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.Set;

/**
 * Acts as a controller interface for an {@link Address} set. This controller is used within a
 * {@link TransactionViewModel} to manipulate an {@link Address} set.
 */
public class AddressViewModel implements HashesViewModel {
    private Address self;
    private Indexable hash;

    /**
     * Constructor for an {@link Address} set controller from a {@link Hash} identifier.
     * @param hash The {@link Hash} identifier that the controller will be created for.
     */
    public AddressViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
     * Constructor for an {@link Address} set controller from an existing {@link Address} set. If the set is empty, an
     * {@link Address} set is created.
     *
     * @param hashes The {@link Address} set that the controller will be created from
     * @param hash The {@link Hash} identifier that acts as a reference for the {@link Address} set
     */
    private AddressViewModel(Address hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Address(): hashes;
        this.hash = hash;
    }

    /**
     * Creates a new {@link Address} set controller. This controller is created by extracting the {@link Address} set
     * from the database using the provided {@link Hash} identifier.
     *
     * @param tangle The tangle reference for the database to find the {@link Address} set in
     * @param hash The hash identifier for the {@link Address} set that needs to be found
     * @return The {@link AddressViewModel} controller generated
     * @throws Exception Thrown if the database cannot load an {@link Address} set from the reference {@link Hash}
     */
    public static AddressViewModel load(Tangle tangle, Indexable hash) throws Exception {
        return new AddressViewModel((Address) tangle.load(Address.class, hash), hash);
    }

    /**
     * Stores the {@link Address} set indexed by its {@link Hash} identifier in the database.
     *
     * @param tangle The tangle reference for the database
     * @return True if the object was saved correctly, False if not
     * @throws Exception Thrown if the {@link Address} set or index {@link Hash} are null
     */
    public boolean store(Tangle tangle) throws Exception {
        return tangle.save(self, hash);
    }

    /**@return The size of the {@link Address} set referenced by the controller*/
    public int size() {
        return self.set.size();
    }

    /**
     * Adds the {@link Address} set referenced by the provided {@link Hash} to the stored {@link Address} set.
     *
     * @param theHash The {@link Hash} identifier to be added to the set
     * @return True if the {@link Address} set is added correctly, False if not
     */
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    /**@return The index {@link Hash} identifier of the {@link Address} set*/
    public Indexable getIndex() {
        return hash;
    }

    /**@return The {@link Address} set referenced by the controller*/
    public Set<Hash> getHashes() {
        return self.set;
    }

    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Address.class,hash);
    }

    /**
     * Fetches the first persistable {@link Address} set from the database and generates a new {@link AddressViewModel}
     * from it. If no {@link Address} sets exist in the database, it will return a null pair.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link AddressViewModel}
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
     * Fetches the next indexed persistable {@link Address} set from the database and generates a new
     * {@link AddressViewModel} from it. If no {@link Address} sets exist in the database, it will return a null pair.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link AddressViewModel}
     * @throws Exception Thrown if the database fails to return a next {@link Address} set
     */
    public AddressViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Address.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new AddressViewModel((Address) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }
}
