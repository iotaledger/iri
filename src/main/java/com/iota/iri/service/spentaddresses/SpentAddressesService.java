package com.iota.iri.service.spentaddresses;

import com.iota.iri.model.Hash;

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
    boolean wasAddressSpentFrom(Hash addressHash) throws SpentAddressesException;

    /**
     * Calculate all spent addresses in between a range
     * 
     * @param fromMilestoneIndex the lower bound milestone index (inclusive)
     * @param toMilestoneIndex the upper bound milestone index (exclusive)
     * @throws Exception when anything went wrong whilst calculating.
     */
    void calculateSpentAddresses(int fromMilestoneIndex, int toMilestoneIndex) throws Exception;
}
