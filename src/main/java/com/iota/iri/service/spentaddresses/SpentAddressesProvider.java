package com.iota.iri.service.spentaddresses;

import com.iota.iri.model.Hash;

public interface SpentAddressesProvider {
    void containsAddress(Hash addressHash);

    void addAddress(Hash addressHash);

    void shutdown();

    void writeSpentAddressesToDisk(String basePath);
}
