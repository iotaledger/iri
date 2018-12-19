package com.iota.iri.service.spentaddresses;

import com.iota.iri.model.Hash;

public interface SpentAddressesService {
    boolean wasAddressSpent(Hash addressHash);
}
