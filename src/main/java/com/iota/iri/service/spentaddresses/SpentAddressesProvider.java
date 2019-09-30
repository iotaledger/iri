package com.iota.iri.service.spentaddresses;

import com.iota.iri.model.Hash;

import java.util.Collection;
import java.util.List;

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
    
    /**
     * Loads all spent addresses we know of in a collection
     * 
     * @return The spent addresses
     * @throws SpentAddressesException If the provider fails read
     */
    //used by IXI
    List<Hash> getAllAddresses();

    /**
     * Starts the SpentAddressesProvider by reading the previous spent addresses from files.
     * @param assertSpentAddressesExistence a flag that forces an assertion that
     *        we have spent addresses data at startup
     *
     * @throws SpentAddressesException if we failed to create a file at the designated location
     */
    void init(boolean assertSpentAddressesExistence) throws SpentAddressesException;

}
