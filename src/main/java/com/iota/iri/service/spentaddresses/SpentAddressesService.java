package com.iota.iri.service.spentaddresses;

import com.iota.iri.model.Hash;

public interface SpentAddressesService {
    boolean wasAddressSpentFrom(Hash addressHash) throws SpentAddressesException;

    void calculateSpentAddresses(int fromMilestoneIndex, int toMilestoneIndex) throws Exception;
}
