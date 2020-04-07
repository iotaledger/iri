package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;

/**
 * In charge of solidifying unsolid transactions we walked on during tipsel
 */
public interface TipSelSolidifier {

    /**
     * Solidify a give transaction
     *
     * @param transactionHash hash of transaction to solidify
     */
    void solidify(Hash transactionHash);
}
