package com.iota.iri.storage;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Hashes;

import java.util.List;

public interface PermanentPersistenceProvider {


    /**
     * Pins a {@code model} for permanent storage.
     * @param model the transaction to store.
     * @param index the Hash for indexing
     * @return true of succesfully pinnend
     * @throws Exception
     */
    boolean pinTransaction(TransactionViewModel model, Hash index) throws Exception;

    /***
     * Unpins a transaction, removing it from permanent storage
     * @param index the transaction ID
     * @return true of succesfully unpinnend
     * @throws Exception
     */
    boolean unpinTransaction(Hash index) throws Exception;

    /***
     * Checks if transactions are pinnend or not
     * @param indexes
     * @return List of booleans in order of the list of hashes given. True is pinned, false is not pinned
     * @throws Exception
     */
    boolean[] isPinned(List<Hash> indexes) throws Exception;

    /***
     * Retrieves the transactions from the permanent storage
     * @param index the transaction ID to retrieve
     * @return The transactions
     * @throws Exception
     */
    TransactionViewModel getTransaction(Hash index) throws Exception;

    /***
     * Finds a list of transaction ID's related to the address hash
     * @param index address hash
     * @return List of found transaction ID's
     * @throws Exception
     */
    Hashes findAddress(Hash index) throws Exception;
    /***
     * Finds a list of transaction ID's related to the bundle hash
     * @param index bundle hash
     * @return List of found transaction ID's
     * @throws Exception
     */
    Hashes findBundle(Hash index) throws Exception;
    /***
     * Finds a list of transaction ID's related to the tag
     * @param index tag
     * @return List of found transaction ID's
     * @throws Exception
     */
    Hashes findTag(Hash index) throws Exception;
    /***
     * Finds a list of approvee's related to the transaction ID
     * @param index transaction ID
     * @return List of found approvee transaction ID's
     * @throws Exception
     */
    Hashes findApprovee(Hash index) throws Exception;
}
