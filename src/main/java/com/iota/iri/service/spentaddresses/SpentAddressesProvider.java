package com.iota.iri.service.spentaddresses;

import com.iota.iri.model.Hash;

import java.util.Collection;

/**
 * Find, mark and store spent addresses
 */
public interface SpentAddressesProvider {
    /**
     * Checks if this address hash has been spent from
     * 
     * @param addressHash The address to check for
     * @return <code>true</code> if it is, else <code>false</code>
     * @throws SpentAddressesException If the provider fails to check the address
     */
    boolean containsAddress(Hash addressHash) throws SpentAddressesException;

    /**
     * Mark an address as spent.
     * 
     * @param addressHash the address which we want to mark.
     * @throws SpentAddressesException If the provider fails to add the address
     */
    void saveAddress(Hash addressHash) throws SpentAddressesException;
    
    /**
     * Mark all addresses as spent.
     * 
     * @param addressHashes The addresses we want to mark
     * @throws SpentAddressesException If the provider fails to add an address
     */
    void saveAddressesBatch(Collection<Hash> addressHashes) throws SpentAddressesException;

}
