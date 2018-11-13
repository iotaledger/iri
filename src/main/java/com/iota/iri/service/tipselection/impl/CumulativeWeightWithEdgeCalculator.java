package com.iota.iri.service.tipselection.impl;

import com.google.gson.Gson;
import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.model.HashPrefix;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.utils.collections.impl.TransformingBoundedHashSet;
import com.iota.iri.utils.collections.impl.TransformingMap;
import com.iota.iri.utils.collections.interfaces.BoundedSet;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import com.iota.iri.storage.Tangle;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Used to create a weighted random walks.
 */
public class CumulativeWeightWithEdgeCalculator extends CumulativeWeightCalculator {

	private static final Logger log = LoggerFactory.getLogger(CumulativeWeightWithEdgeCalculator.class);
	
	private static final Gson gson = new Gson();

	public CumulativeWeightWithEdgeCalculator(Tangle tangle) {
		super(tangle);
	}

	@Override
	public UnIterableMap<HashId, Integer> calculate(Hash entryPoint) throws Exception {
		log.debug("Start calculating cw starting with tx hash {}", entryPoint);

		LinkedHashSet<Hash> txHashesToRate = sortTransactionsInTopologicalOrder(entryPoint);
		return calculateCwInOrder(txHashesToRate);
	}

	// Uses DFS algorithm to sort
	private LinkedHashSet<Hash> sortTransactionsInTopologicalOrder(Hash startTx) throws Exception {
		LinkedHashSet<Hash> sortedTxs = new LinkedHashSet<>();
		Deque<Hash> stack = new ArrayDeque<>();
		Map<Hash, Collection<Hash>> txToDirectApprovers = new HashMap<>();

		stack.push(startTx);
		while (CollectionUtils.isNotEmpty(stack)) {
			Hash txHash = stack.peek();
			if (!sortedTxs.contains(txHash)) {
				Collection<Hash> appHashes = getTxDirectApproversHashes(txHash, txToDirectApprovers);
				if (CollectionUtils.isNotEmpty(appHashes)) {
					Hash txApp = getAndRemoveApprover(appHashes);
					stack.push(txApp);
					continue;
				}
			} else {
				stack.pop();
				continue;
			}
			sortedTxs.add(txHash);
		}

		return sortedTxs;
	}

	private Hash getAndRemoveApprover(Collection<Hash> appHashes) {
		Iterator<Hash> hashIterator = appHashes.iterator();
		Hash txApp = hashIterator.next();
		hashIterator.remove();
		return txApp;
	}

	private Collection<Hash> getTxDirectApproversHashes(Hash txHash, Map<Hash, Collection<Hash>> txToDirectApprovers)
			throws Exception {
		Collection<Hash> txApprovers = txToDirectApprovers.get(txHash);
		if (txApprovers == null) {
			ApproveeViewModel approvers = ApproveeViewModel.load(tangle, txHash);
			Collection<Hash> appHashes = CollectionUtils.emptyIfNull(approvers.getHashes());
			txApprovers = new HashSet<>(appHashes.size());
			for (Hash appHash : appHashes) {
				// if not genesis (the tx that confirms itself)
				if (ObjectUtils.notEqual(Hash.NULL_HASH, appHash)) {
					txApprovers.add(appHash);
				}
			}
			txToDirectApprovers.put(txHash, txApprovers);
		}
		return txApprovers;
	}

	private UnIterableMap<HashId, Integer> calculateCwInOrder(LinkedHashSet<Hash> txsToRate) throws Exception {
		// 计算最大时长以及初始化各边时长
		long maxTimeDiff = 0l;
		long timeDiff = 0l;
		Iterator<Hash> txHashIterator = txsToRate.iterator();
		Map<String, Long> edgeMap = new HashMap<String, Long>();
		Map<Hash, Set<Hash>> approvers = new HashMap<Hash, Set<Hash>>();
		while (txHashIterator.hasNext()) {
			Hash txHash = txHashIterator.next();
			TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, txHash);
			// 获取trunk以及branch
			Hash trunkHash = transactionViewModel.getTrunkTransactionHash();
			Hash branchHash = transactionViewModel.getBranchTransactionHash();
			// 获取trunk以及branch的验证子节点
			Set<Hash> trunkSet = approvers.get(trunkHash) == null ? new HashSet<Hash>() : approvers.get(trunkHash);
			Set<Hash> branchSet = approvers.get(branchHash) == null ? new HashSet<Hash>() : approvers.get(branchHash);
			// 定义边的key
			String trunkEdge = trunkHash.toString() + txHash.toString();
			String branchEdge = branchHash.toString() + txHash.toString();
			// 边的长度存入map缓存
			edgeMap.put(trunkEdge, transactionViewModel.getTrunkTimeDifference());
			edgeMap.put(branchEdge, transactionViewModel.getBranchTimeDifference());
			trunkSet.add(txHash);
			branchSet.add(txHash);
			approvers.put(trunkHash, trunkSet);
			approvers.put(branchHash, branchSet);
			timeDiff = transactionViewModel.getTrunkTimeDifference() > transactionViewModel.getBranchTimeDifference()
					? transactionViewModel.getTrunkTimeDifference()
					: transactionViewModel.getBranchTimeDifference();
			maxTimeDiff = maxTimeDiff > timeDiff ? maxTimeDiff : timeDiff;
		}

		UnIterableMap<HashId, Integer> txHashToCumulativeWeight = createTxHashToCumulativeWeightMap(txsToRate.size());
		
		txHashIterator = txsToRate.iterator();
		Object[] objArray = txsToRate.toArray();
		for (int i = 0, j = objArray.length; i < j; i++) {
			Hash txHash = (Hash) objArray[i];
			txHashToCumulativeWeight = updateCwWithEdge(txHashToCumulativeWeight, txHash, edgeMap, approvers,
					maxTimeDiff, j - i);

		}
		return txHashToCumulativeWeight;
	}

	private UnIterableMap<HashId, Integer> updateCwWithEdge(UnIterableMap<HashId, Integer> txHashToCumulativeWeight,
			Hash txHash, Map<String, Long> edgeMap, Map<Hash, Set<Hash>> approvers, long maxTimeDiff, int position) {
		Set<Hash> approveEdge = approvers.get(txHash) == null ? new HashSet<Hash>() : approvers.get(txHash);
		int weight = position;
		float iteratorWeight = 0;
		Iterator<Hash> setIterator = approveEdge.iterator();
		// 遍历证明的点，点的权重×边权重后相加
		while (setIterator.hasNext()) {
			// 获取点
			Hash prover = setIterator.next();
			// 获取之前点的权重
			int preWeight = txHashToCumulativeWeight.get(prover);
			// 获取边的权重
			float edgeWeight = (float) edgeMap.get(txHash.toString() + prover.toString()) / maxTimeDiff;
			iteratorWeight += edgeWeight * preWeight;
		}
		weight += iteratorWeight;
		txHashToCumulativeWeight.put(txHash, weight);
		return txHashToCumulativeWeight;
	}

	private static UnIterableMap<HashId, Integer> createTxHashToCumulativeWeightMap(int size) {
		return new TransformingMap<>(size, HashPrefix::createPrefix, null);
	}
}
