package com.iota.iri.service.tipselection;


import com.iota.iri.Milestone;
import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.LedgerValidator;

import java.util.Map;

/**
 * @author      IOTA Foundation
 * @version     0.1
 */


public class TipSelector implements EntryPoint,Rating,Walker{

    private final Tangle tangle;
    private final Milestone milestone;
    private final MessageQ messageQ;
    private final LedgerValidator ledgerValidator;
    private final TransactionValidator transactionValidator;
    private final int milestoneStartIndex;
    private final boolean testnet;
    private final int maxDepth;


    public TipSelector(final Tangle tangle,
                       final Milestone milestone,
                       final MessageQ messageQ,
                       final LedgerValidator ledgerValidator,
                       final TransactionValidator transactionValidator,
                       final int maxDepth,
                       final boolean testnet,
                       final int milestoneStartIndex){
        this.tangle = tangle;
        this.milestone = milestone;
        this.messageQ = messageQ;
        this.ledgerValidator = ledgerValidator;
        this.transactionValidator = transactionValidator;
        this.maxDepth = maxDepth;
        this.testnet = testnet;
        this.milestoneStartIndex = milestoneStartIndex;
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
    public Hash getEntryPoint(int depth) throws Exception{

        if(depth > maxDepth){
            depth = maxDepth;
        }

        int milestoneIndex = Math.max(milestone.latestSolidSubtangleMilestoneIndex - depth - 1,0);
        MilestoneViewModel milestoneViewModel =
                    MilestoneViewModel.findClosestNextMilestone(tangle, milestoneIndex,testnet,milestoneStartIndex);
        if(milestoneViewModel != null && milestoneViewModel.getHash() != null){
            return milestoneViewModel.getHash();
        }

        return milestone.latestSolidSubtangleMilestone;

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
    public Map<Hash, Long> calculate(Hash entryPoint){

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
    public Hash walk(Hash entryPoint, Map<Hash, Long> ratings, int maxIndex){

    }


}

