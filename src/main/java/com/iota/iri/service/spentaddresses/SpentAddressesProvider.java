package com.iota.iri.service.spentaddresses;

import com.iota.iri.model.Hash;

public interface SpentAddressesProvider {
    boolean containsAddress(Hash addressHash) throws SpentAddressesException;

    void addAddress(Hash addressHash) throws SpentAddressesException;

    void writeSpentAddressesToDisk(String basePath) throws SpentAddressesException;
}
