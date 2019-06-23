package com.iota.iri.pluggables.utxo;

import com.google.gson.Gson;
import com.iota.iri.model.Hash;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;
import static org.junit.Assert.*;

public class UTXOGraphTest {
	private static final Logger log = LoggerFactory.getLogger(UTXOGraphTest.class);
	private static UTXOGraph graph = new UTXOGraph();
	private static List<Hash> order = new ArrayList<>();
	private static HashMap<String, Hash> txnToTangleMap = new HashMap<>();
	private List<Txn> transactions = new ArrayList<>();


	@Test
	public void testDoubleSpendCase1() {
		/* Transactions graph as following. the second 'b' and 'A' is doubleSpent.
		 *         A
		 *       /|  |  \
		 *      / |  \  \
		 *    |b  A|  b  A
		 *     \    /
		 *       c
		 */

		/*
		 * addTxn
		 */
		// add genesis
		Txn genesis = TransactionData.genesis();
		//log.info("genesis = \n{}", genesis);
		graph.addTxn(genesis, 0);
		transactions.add(genesis);

		// ---- txn1: A sent 1000 to b
		Txn txn1 = new Txn();

		TxnIn txnIn1 = new TxnIn();
		txnIn1.txnHash = genesis.txnHash;
		txnIn1.idx = 0;
		txnIn1.userAccount = "A";
		txn1.inputs = new ArrayList<>();
		txn1.inputs.add(txnIn1);

		TxnOut txnOut1b = new TxnOut();
		txnOut1b.amount = 1000;
		txnOut1b.userAccount = "b";
		TxnOut txnOut1A = new TxnOut();
		txnOut1A.amount = 999999000;
		txnOut1A.userAccount = "A";

		List<TxnOut> txnOutList = new ArrayList<>();
		txnOutList.add(txnOut1b);
		txnOutList.add(txnOut1A);
		txn1.outputs = txnOutList;

		txn1.txnHash = TransactionData.generateHash(new Gson().toJson(txn1));

		graph.addTxn(txn1, 1);
		transactions.add(txn1);
		Hash txn1Hash = getRandomTransactionHash();
		order.add(txn1Hash);
		txnToTangleMap.put(txn1.txnHash, txn1Hash);

		// ----- txn2: A sent 10 to b use the same out
		Txn txn2 = new Txn();

		TxnIn txnIn2 = new TxnIn();
		txnIn2.txnHash = genesis.txnHash;
		txnIn2.idx = 0;
		txnIn2.userAccount = "A";
		txn2.inputs = new ArrayList<>();
		txn2.inputs.add(txnIn2);

		TxnOut txOut2b = new TxnOut();
		txOut2b.amount = 10;
		txOut2b.userAccount = "b";
		TxnOut txOut2A = new TxnOut();
		txOut2A.amount = 999999990;
		txOut2A.userAccount = "A";

		txn2.outputs = new ArrayList<>();
		txn2.outputs.add(txOut2b);
		txn2.outputs.add(txOut2A);

		txn2.txnHash = TransactionData.generateHash(new Gson().toJson(txn2));

		graph.addTxn(txn2, 2);
		transactions.add(txn2);
		Hash txn2Hash = getRandomTransactionHash();
		order.add(txn2Hash);
		txnToTangleMap.put(txn2.txnHash, txn2Hash);


		// ----- txn3: b sent 100 to c
		Txn txn3 = new Txn();

		TxnIn txnIn3b1 = new TxnIn();
		txnIn3b1.txnHash = txn2.txnHash;
		txnIn3b1.idx = 0;
		txnIn3b1.userAccount = "b";

		TxnIn txnIn3b2 = new TxnIn();
		txnIn3b2.txnHash = txn1.txnHash;
		txnIn3b2.idx = 0;
		txnIn3b2.userAccount = "b";

		txn3.inputs = new ArrayList<>();
		txn3.inputs.add(txnIn3b1);
		txn3.inputs.add(txnIn3b2);

		TxnOut txnOut3c = new TxnOut();
		txnOut3c.amount = 100;
		txnOut3c.userAccount = "c";

		TxnOut txnOut3b = new TxnOut();
		txnOut3b.amount = 910;
		txnOut3b.userAccount = "b";

		txn3.outputs = new ArrayList<>();
		txn3.outputs.add(txnOut3c);
		txn3.outputs.add(txnOut3b);

		txn3.txnHash = TransactionData.generateHash(new Gson().toJson(txn3));

		graph.addTxn(txn3, 3);
		transactions.add(txn3);
		Hash txn3Hash = getRandomTransactionHash();
		order.add(txn3Hash);
		txnToTangleMap.put(txn2.txnHash, txn3Hash);

		assertEquals(graph.outGraph.size(), 4);
		assertEquals(graph.inGraph.size(), 7);
		assertEquals(graph.accountMap.size(), 3);

		/*
		 * markDoubleSpend
		 */
		graph.markDoubleSpend(order, txnToTangleMap);
		assertEquals(graph.doubleSpendSet.size(), 1);


		/*
		 * isDoubleSpend
		 */
		assertEquals(false, graph.isDoubleSpend(genesis.txnHash + ":" + 0 +","+ "A"));

		assertEquals(false, graph.isDoubleSpend(txn1.txnHash + ":" + 0 +","+ "b"));
		assertEquals(false, graph.isDoubleSpend(txn1.txnHash + ":" + 1 +","+ "A"));

		assertEquals(true, graph.isDoubleSpend(txn2.txnHash + ":" + 0 +","+ "b"));
		assertEquals(true, graph.isDoubleSpend(txn2.txnHash + ":" + 1 +","+ "A"));

		assertEquals(true, graph.isDoubleSpend(txn3.txnHash + ":" + 0 +","+ "c"));
		assertEquals(true, graph.isDoubleSpend(txn3.txnHash + ":" + 1 +","+ "b"));

		/*
		 * isSpent
		 */
		assertEquals(true, graph.isSpent(genesis.txnHash + ":" + 0 +","+ "A"));

		assertEquals(false, graph.isSpent(txn1.txnHash + ":" + 0 +","+ "b"));
		assertEquals(false, graph.isSpent(txn1.txnHash + ":" + 1 +","+ "A"));

		assertEquals(false, graph.isSpent(txn2.txnHash + ":" + 0 +","+ "b"));
		assertEquals(false, graph.isSpent(txn2.txnHash + ":" + 1 +","+ "A"));

		assertEquals(false, graph.isSpent(txn3.txnHash + ":" + 0 +","+ "c"));
		assertEquals(false, graph.isSpent(txn3.txnHash + ":" + 1 +","+ "b"));

		/*
		 * findUnspentTxnsForAccount
		 */
		// FIXME: the result returned by 'findUnspentTxnsForAccount' may contain ILLEGAL(doubleSpend) utxos.
		Set<Integer> setA = graph.findUnspentTxnsForAccount("A");
		Set<Integer> setb = graph.findUnspentTxnsForAccount("b");
		Set<Integer> setc = graph.findUnspentTxnsForAccount("c");
		assertEquals(setA.size(), 2);
		assertEquals(setb.size(), 3);
		assertEquals(setc.size(), 1);

		/*
		 * printGraph
		 */
		Set<String> spend = new HashSet<>();
		long tot = 0;
		for (int i = 0; i < transactions.size(); i++) {
			Txn transaction = transactions.get(i);
			List<TxnOut> l = transaction.outputs;
			for (int j = 0; j < l.size(); j++) {
				TxnOut txnOut = l.get(j);
				String key = transaction.txnHash + ":" + j + "," + txnOut.userAccount;
				if (!graph.isSpent(key) && !graph.isDoubleSpend(key)) {
					tot += txnOut.amount;
					spend.add(key);
				}
			}
		}
		assertEquals(1000000000, tot);
		//graph.printGraph(graph.outGraph, "graph.dot", spend);
	}
}