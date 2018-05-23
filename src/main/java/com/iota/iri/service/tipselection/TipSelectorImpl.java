//package com.iota.iri.service.tipselection;
//
//import com.iota.iri.LedgerValidator;
//import com.iota.iri.Milestone;
//import com.iota.iri.TransactionValidator;
//import com.iota.iri.controllers.MilestoneViewModel;
//import com.iota.iri.model.Hash;
//import com.iota.iri.service.tipselection.impl.RatingOne;
//import com.iota.iri.storage.Tangle;
//import com.iota.iri.zmq.MessageQ;
//
//public class TipSelectorImpl implements EntryPoint,TipSelector,Walker {
//
//
//    private Tangle tangle;
//    private Milestone milestone;
//    private MessageQ messageQ;
//    private LedgerValidator ledgerValidator;
//    private TransactionValidator transactionValidator;
//    private int milestoneStartIndex;
//
//    private boolean testnet;
//
//
//    RatingCalculator ratingCumulative = new RatingOne();
//
//
//
//    public TipSelectorImpl(Tangle tangle,
//                           Milestone milestone,
//                           MessageQ messageQ,
//                           LedgerValidator ledgerValidator,
//                           TransactionValidator transactionValidator,
//                           int milestoneStartIndex,
//                           boolean testnet)
//    {
//        this.tangle = tangle;
//        this.milestone = milestone;
//        this.messageQ = messageQ;
//        this.ledgerValidator = ledgerValidator;
//        this.transactionValidator = transactionValidator;
//        this.milestoneStartIndex = milestoneStartIndex;
//        this.testnet = testnet;
//    }
//
//
//    public Hash getEntryPoint(int depth) throws Exception {
//        int milestoneIndex = Math.max(milestone.latestSolidSubtangleMilestoneIndex - depth - 1,0);
//        MilestoneViewModel milestoneViewModel =
//                MilestoneViewModel.findClosestNextMilestone(tangle, milestoneIndex,testnet,milestoneStartIndex);
//        if(milestoneViewModel != null && milestoneViewModel.getHash() != null){
//            return milestoneViewModel.getHash();
//        }
//
//        return milestone.latestSolidSubtangleMilestone;
//    }
//
//}
