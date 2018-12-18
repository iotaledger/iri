package com.iota.iri.validator.impl;

import com.iota.iri.controllers.*;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.storage.Tangle;
import com.iota.iri.validator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class LedgerValidatorNull extends LedgerValidatorImpl{

    private final Logger log = LoggerFactory.getLogger(LedgerValidatorNull.class);

    public LedgerValidatorNull(Tangle tangle, MilestoneTracker milestoneTracker, TransactionRequester transactionRequester, MessageQ messageQ) {
        super( tangle,  milestoneTracker,  transactionRequester,  messageQ);
    }

    public Map<Hash,Long> getLatestDiff(final Set<Hash> visitedNonMilestoneSubtangleHashes, Hash tip, int latestSnapshotIndex, boolean milestone) throws Exception 
    {
        return super.getLatestDiff(visitedNonMilestoneSubtangleHashes, tip, latestSnapshotIndex, milestone);
    }

    public void init() throws Exception {
        super.init();
    }

    public boolean updateSnapshot(MilestoneViewModel milestoneVM) throws Exception {
       return super.updateSnapshot(milestoneVM);
    }

    public boolean checkConsistency(List<Hash> hashes) throws Exception {
        return true;
    }

    public boolean updateDiff(Set<Hash> approvedHashes, final Map<Hash, Long> diff, Hash tip) throws Exception {
        return super.updateDiff(approvedHashes, diff, tip);
    }
}
