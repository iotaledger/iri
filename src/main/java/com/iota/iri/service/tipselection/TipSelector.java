package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;
import java.util.List;
import java.util.Optional;

/**
 * This interface is used for gathering tips
 */


public interface TipSelector {

    /**
     *Method for finding tips
     *
     * <p>
     *  This method is used to find tips for approval based off of an entry reference ID
     *  and a given depth.
     * </p>
     *
     * @param reference  The transaction ID reference for entry.
     * @param depth  The depth that the transactions will be found from.
     * @return  Transactions for
     */
    List<Hash> getTransactionsToApprove(Optional<Hash> reference, int depth) throws Exception;

    int getMaxDepth();
}
