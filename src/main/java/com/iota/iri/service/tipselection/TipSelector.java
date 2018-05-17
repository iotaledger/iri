package com.iota.iri.service.tipselection;


import com.iota.iri.Milestone;
import com.iota.iri.TransactionValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.LedgerValidator;

import java.util.Map;

/**
 * @author      IOTA Foundation
 * @version     0.1
 */


public class TipSelector implements EntryPoint,Rating,Walker{

    public final Tangle tangle;
    public final Milestone reference;
    public final MessageQ messageQ;
    public final LedgerValidator ledgerValidator;
    public final TransactionValidator transactionValidator;



    public TipSelector(final Tangle tangle,
                       final Milestone reference,
                       final MessageQ messageQ,
                       final LedgerValidator ledgerValidator,
                       final TransactionValidator transactionValidator){
        this.tangle = tangle;
        this.reference = reference;
        this.messageQ = messageQ;
        this.ledgerValidator = ledgerValidator;
        this.transactionValidator = transactionValidator;
    }


    /**
     *Initialize
     */
    void init(){

    }

    void getTransactionsToApprove(){

    }


    /**
     *Entry point generator for tip selection
     *<p>
     *Uses reference point and depth to determine the entry point for
     *the random walk.
     *</p>
     *
     * @param depth  Depth in milestones used for random walk
     * @return  Entry point for walk method
     */
    public void getEntryPoint(int depth){

    }


    /**
     * Cumulative rating calculator
     * <p>
     * Calculates the cumulative rating of the transactions that reference
     * a given entry point.
     * </p>
     *
     * @param entryPoint  Transaction ID of selected milestone.
     * @return  Hash Map of cumulative ratings.
     */
    public void calculate(Hash entryPoint){

    }



    /**
     * Walk algorithm
     * <p>
     * Starts from given entry point to select valid transactions to be used
     * as tips. It will output valid transactions as tips.
     * </p>
     *
     * @param entryPoint  Transaction ID of milestone to start walk from.
     * @param ratings  Mapped ratings associated with Transaction ID.
     * @param maxIndex  The deepest milestone index allowed for referencing.
     * @return  Transaction ID of tip.
     */
    public void walk(Hash entryPoint, Map<Hash, Long> ratings, int maxIndex){

    }


}

