package com.iota.iri.service.spentaddresses;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

import java.util.Collection;
import java.util.Set;

/**
 * 
 * Check and calculate spent addresses
 *
 */
public interface SpentAddressesService {
    
    /**
     * 
     * @param addressHash
     * @return <code>true</code> if it was, else <code>false</code>
     * @throws SpentAddressesException
     */
    boolean wasAddressSpentFrom(Hash addressHash, Set<Hash> addressesChecked) throws SpentAddressesException;

    /**
     * Calculates and persists all spent addresses in between a range that were validly signed
     * 
     * @param fromMilestoneIndex the lower bound milestone index (inclusive)
     * @param toMilestoneIndex the upper bound milestone index (exclusive)
     * @throws Exception when anything went wrong whilst calculating.
     */
    void persistSpentAddresses(int fromMilestoneIndex, int toMilestoneIndex) throws Exception;

    /**
     * Persist all the verifiable spent from a given list of transactions
     * @param transactions transactions to obtain spends from
     * @throws SpentAddressesException
     */
    void persistSpentAddresses(Collection<TransactionViewModel> transactions) throws SpentAddressesException;
}
