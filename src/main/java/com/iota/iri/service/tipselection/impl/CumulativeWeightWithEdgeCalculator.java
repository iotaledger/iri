package com.iota.iri.service.tipselection.impl;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.model.HashPrefix;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.impl.TransformingMap;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;

/**
 * Used to create a weighted random walks.
 */
public class CumulativeWeightWithEdgeCalculator extends CumulativeWeightCalculator {

	private static final Logger log = LoggerFactory.getLogger(CumulativeWeightWithEdgeCalculator.class);

	private static final int UNIT_WEIGHT = 1;

        private boolean isUseUnifiedEdgeWeight;

	public CumulativeWeightWithEdgeCalculator(Tangle tangle) {
		super(tangle);
                isUseUnifiedEdgeWeight = false;
	}

        public void setIsUseUnifiedEdgeWeight(boolean isUseUnifiedEdgeWeight)
        {
            this.isUseUnifiedEdgeWeight = isUseUnifiedEdgeWeight;
        }

	@Override
	public UnIterableMap<HashId, Integer> calculate(Hash entryPoint) throws Exception {
		log.debug("Start calculating cw starting with tx hash {}", entryPoint);

		LinkedHashSet<Hash> txHashesToRate = sortTransactionsInTopologicalOrder(entryPoint);
		return calculateCwInOrder(txHashesToRate);
	}

	private UnIterableMap<HashId, Integer> calculateCwInOrder(LinkedHashSet<Hash> txsToRate) throws Exception {

		Iterator<Hash> txHashIterator = txsToRate.iterator();
		Map<String, Long> edgeMap = new HashMap<String, Long>();
		Map<Hash, Set<Hash>> approvers = new HashMap<Hash, Set<Hash>>();
		long maxTimeDiff = preprocessDatas(edgeMap, approvers, txsToRate);

		UnIterableMap<HashId, Integer> txHashToCumulativeWeight = createTxHashToCumulativeWeightMap(txsToRate.size());
		// avoid decimal loss,define a float map
		Map<HashId, Float> txHashToCumulativeWeightFloat = new HashMap<HashId, Float>();
		txHashIterator = txsToRate.iterator();
		while (txHashIterator.hasNext()) {
			Hash txHash = txHashIterator.next();
			txHashToCumulativeWeightFloat = updateCwWithEdge(txHashToCumulativeWeightFloat, txHash, edgeMap, approvers,
					maxTimeDiff, txHashToCumulativeWeight);
		}
		// parse the float map to the result in need
		return txHashToCumulativeWeight;
	}

	// get max edge weight,points-provers map and the directedEdge-weight map
	private long preprocessDatas(Map<String, Long> edgeMap, Map<Hash, Set<Hash>> approvers,
			LinkedHashSet<Hash> txsToRate) throws Exception {
		// calculate the max time difference
		long maxTimeDiff = 0, timeDiff = 0, trunkDiff = 0, branchDiff = 0;
		Iterator<Hash> txHashIterator = txsToRate.iterator();
		while (txHashIterator.hasNext()) {
			Hash txHash = txHashIterator.next();
			TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, txHash);
			// get trunk and branch
			TransactionViewModel trunk = TransactionViewModel.fromHash(tangle,
					transactionViewModel.getTrunkTransactionHash());
			TransactionViewModel branch = TransactionViewModel.fromHash(tangle,
					transactionViewModel.getBranchTransactionHash());
			trunkDiff = transactionViewModel.getArrivalTime() - trunk.getArrivalTime();
			branchDiff = transactionViewModel.getArrivalTime() - branch.getArrivalTime();
			Hash trunkHash = trunk.getHash();
			Hash branchHash = branch.getHash();
			// get provers by trunk and branch
			Set<Hash> trunkSet = approvers.get(trunkHash) == null ? new HashSet<Hash>() : approvers.get(trunkHash);
			Set<Hash> branchSet = approvers.get(branchHash) == null ? new HashSet<Hash>() : approvers.get(branchHash);
			// define the directed edge as the key
			String trunkEdge = trunkHash.toString() + txHash.toString();
			String branchEdge = branchHash.toString() + txHash.toString();
			// put the edge weight into the map
			edgeMap.put(trunkEdge, trunkDiff);
			edgeMap.put(branchEdge, branchDiff);
			trunkSet.add(txHash);
			branchSet.add(txHash);
			approvers.put(trunkHash, trunkSet);
			approvers.put(branchHash, branchSet);
			if (trunk.getArrivalTime() != 0 && branch.getArrivalTime() != 0) {
				timeDiff = trunkDiff > branchDiff ? trunkDiff : branchDiff;
				maxTimeDiff = maxTimeDiff > timeDiff ? maxTimeDiff : timeDiff;
			}
		}
		return maxTimeDiff;
	}

	private Map<HashId, Float> updateCwWithEdge(Map<HashId, Float> txHashToCumulativeWeightFloat, Hash txHash,
			Map<String, Long> edgeMap, Map<Hash, Set<Hash>> approvers, long maxTimeDiff,
			UnIterableMap<HashId, Integer> txHashToCumulativeWeight) {
		Set<Hash> approveEdge = approvers.get(txHash) == null ? new HashSet<Hash>() : approvers.get(txHash);
		float weight = UNIT_WEIGHT;
		float iteratorWeight = 0;
		Iterator<Hash> setIterator = approveEdge.iterator();
		// loop all points
		while (setIterator.hasNext()) {
			Hash prover = setIterator.next();
			// get the point's weight
			float preWeight = txHashToCumulativeWeightFloat.get(prover);
			// get the edge weight
                        float edgeWeight = 1;
                        if(!isUseUnifiedEdgeWeight) {
                            edgeWeight = (float) edgeMap.get(txHash.toString() + prover.toString()) / maxTimeDiff;
                        }
			iteratorWeight += edgeWeight * preWeight;
		}
		weight += iteratorWeight;
		txHashToCumulativeWeightFloat.put(txHash, weight);
		txHashToCumulativeWeight.put(txHash, Math.round(weight));
		return txHashToCumulativeWeightFloat;
	}

	private static UnIterableMap<HashId, Integer> createTxHashToCumulativeWeightMap(int size) {
		return new TransformingMap<>(size, HashPrefix::createPrefix, null);
	}
}
