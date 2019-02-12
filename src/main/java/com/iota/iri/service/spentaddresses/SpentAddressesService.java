package com.iota.iri.service.spentaddresses;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

import java.util.Collection;

/**
 * 
 * Check and calculate spent addresses
 *
 */
public interface SpentAddressesService {
    
    /**
     * Checks whether the address is associated with a valid signed output
     *
     * @param addressHash the address in question
     * @return <code>true</code> if the address was spent from, else <code>false</code>
     * @throws SpentAddressesException
     */
    boolean wasAddressSpentFrom(Hash addressHash) throws SpentAddressesException;


    /**
     * Persist all the verifiable spent from a given list of transactions
     * @param transactions transactions to obtain spends from
     * @throws SpentAddressesException
     */
    void persistSpentAddresses(Collection<TransactionViewModel> transactions) throws SpentAddressesException;

    /**
     * Persists the spent addresses of all pre-verified valid transactions in an asynchronous manner
     *
     * @param transactions <b>Transactions that have their signatures verified beforehand</b>.
     *                     Non spent transactions will be filtered out when persisting
     */
    void persistValidatedSpentAddressesAsync(Collection<TransactionViewModel> transactions);
}
