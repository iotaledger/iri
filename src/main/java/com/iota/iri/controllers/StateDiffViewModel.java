package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.StateDiff;
import com.iota.iri.storage.Tangle;

import java.util.Map;

/**
 * Acts as a controller interface for a {@link StateDiff}. This controller is used to manipulate a {@link StateDiff}
 * mapping of {@link com.iota.iri.model.persistables.Hashes} to <tt>Balance</tt> states in the database.
 */
public class StateDiffViewModel {
    private StateDiff stateDiff;
    private Hash hash;

    /**
     * Creates a {@link StateDiff} controller using a {@link Hash} identifier as a reference point. A {@link StateDiff}
     * is loaded from the database using the {@link Hash} reference, and the {@link Hash} identifier is set as the
     * controller reference as well.
     *
     * @param tangle The tangle reference for the database
     * @param hash The {@link Hash} identifier of the {@link StateDiff} the controller will be created for
     * @return The new {@link StateDiffViewModel}
     * @throws Exception Thrown if there is an error loading the {@link StateDiff} from the database
     */
    public static StateDiffViewModel load(Tangle tangle, Hash hash) throws Exception {
        return new StateDiffViewModel((StateDiff) tangle.load(StateDiff.class, hash), hash);
    }

    /**
     * Constructor for a {@link StateDiff} controller using a predefined {@link StateDiff} mapping. The {@link Hash}
     * identifier is assigned as a reference for the controller, and the state is stored in the controller.
     *
     * @param state The {@link StateDiff} mapping that the controller will be made for
     * @param hash The reference {@link Hash} identifier
     */
    public StateDiffViewModel(final Map<Hash, Long> state, final Hash hash) {
        this.hash = hash;
        this.stateDiff = new StateDiff();
        this.stateDiff.state = state;
    }

    /**
     * This method checks the {@link com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider} to determine if an object
     * might exist in the database. If it definitively does not exist, it will return False
     *
     * @param tangle The tangle reference for the database
     * @param hash The {@link Hash} identifier of the object you are looking for
     * @return True if the key might exist in the database, False if it definitively does not
     * @throws Exception Thrown if there is an error checking the database
     */
    public static boolean maybeExists(Tangle tangle, Hash hash) throws Exception {
        return tangle.maybeHas(StateDiff.class, hash);
    }

    /**
     * Creates a finalized {@link StateDiff} controller. The referenced {@link StateDiff} of this controller and its
     * reference {@link Hash} identifier cannot be modified. If the provided {@link StateDiff} is null, an empty
     * {@link StateDiff} will be created.
     *
     * @param diff The finalized {@link StateDiff} the controller will be made for
     * @param hash The finalized {@link Hash} identifier of the controller
     */
    private StateDiffViewModel(final StateDiff diff, final Hash hash) {
        this.hash = hash;
        this.stateDiff = diff == null || diff.state == null ? new StateDiff(): diff;
    }

    /**@return True if the {@link StateDiff} is empty, False if there is a variable present*/
    public boolean isEmpty() {
        return stateDiff == null || stateDiff.state == null || stateDiff.state.size() == 0;
    }

    /**@return The {@link Hash} identifier of the {@link StateDiff} controller */
    public Hash getHash() {
        return hash;
    }

    /**@return The {@link StateDiff} map of the controller*/
    public Map<Hash, Long> getDiff() {
        return stateDiff.state;
    }

    /**
     * Saves the {@link StateDiff} and referencing {@link Hash} identifier to the database.
     *
     * @param tangle The tangle reference for the database
     * @return True if the {@link StateDiff} was saved correctly, False if not
     * @throws Exception Thrown if there is an error while saving the {@link StateDiff}
     */
    public boolean store(Tangle tangle) throws Exception {
        //return Tangle.instance().save(stateDiff, hash).get();
        return tangle.save(stateDiff, hash);
    }

    /**
     * Deletes the {@link StateDiff} and referencing {@link Hash} identifier from the database.
     *
     * @param tangle The tangle reference for the database
     * @throws Exception Thrown if there is an error while removing the {@link StateDiff}
     */
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(StateDiff.class, hash);
    }
}
