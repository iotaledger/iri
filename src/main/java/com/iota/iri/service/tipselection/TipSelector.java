package com.iota.iri.service.tipselection;

import java.util.List;
import java.util.Optional;

import com.iota.iri.model.Hash;

/**
 * Selects tips to be approved by a new transaction.
 */


public interface TipSelector {

    /**
     * Method for finding tips.
     *
     * <p>
     *  This method is used to find tips for approval given a {@code depth},
     *  if {@code reference} is present then tips will also reference this transaction.
     * </p>
     *
     * @param depth  The depth that tip selection will start from.
     * @param reference  An optional transaction hash to be referenced by tips.
     * @return  Tips to approve
     * @throws Exception If DB fails to retrieve transactions
     */
    List<Hash> getTransactionsToApprove(int depth, Optional<Hash> reference) throws Exception;
}
