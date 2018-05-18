package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;
import java.util.Collection;

/**
 * This interface is used to enforce usage of the init() and
 * getTransactionsToApprove() methods for gathering tips while
 * extending the usage of EntryPoint,RatingCalculator and Walker
 *
 */


public interface TipSelector extends EntryPoint,RatingCalculator,Walker{

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
    Collection<Hash> getTransactionsToApprove(Hash reference, int depth);

    /**
     *Initialize
     */
    void init();

}

