package com.iota.iri.validator;

import com.iota.iri.controllers.*;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public interface LedgerValidator {



    public Map<Hash,Long> getLatestDiff(final Set<Hash> visitedNonMilestoneSubtangleHashes, Hash tip, int latestSnapshotIndex, boolean milestone) throws Exception;

    public void init() throws Exception ;
    
    public boolean updateSnapshot(MilestoneViewModel milestoneVM) throws Exception ;

    public boolean checkConsistency(List<Hash> hashes) throws Exception ;

    public boolean updateDiff(Set<Hash> approvedHashes, final Map<Hash, Long> diff, Hash tip) throws Exception ;
}
