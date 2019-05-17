package com.iota.iri.pluggables.utxo;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.iota.iri.hash.Curl;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.localinmemorygraph.LocalInMemoryGraphProvider;
import com.iota.iri.utils.Pair;
import io.ipfs.api.IPFS;
import io.ipfs.multihash.Multihash;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

import  com.iota.iri.utils.Converter;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.json.JSONArray;

import com.iota.iri.utils.IotaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionData {
    private static final Logger log = LoggerFactory.getLogger(TransactionData.class);

    Tangle tangle;
    List<Txn> transactions;
    HashMap<String, Hash> txnToTangleMap;
    HashMap<Hash, HashSet<Txn>> tangleToTxnMap;
    List<List<Txn>> tmpStorage;
    UTXOGraph utxoGraph;

    private static TransactionData txnData = new TransactionData();

    public void setTangle(Tangle tangle) {
        if(this.tangle == null) {
            this.tangle = tangle;
        }
    }

    public static TransactionData getInstance() {
        return txnData;
    }

    public TransactionData() {
        txnToTangleMap = new HashMap<String, Hash>();
        tangleToTxnMap = new HashMap<Hash, HashSet<Txn>>();
        tmpStorage = new ArrayList<>();
        utxoGraph = new UTXOGraph();
        init();
    }


    public void restoreTxs(){
        try {
            Pair<Indexable, Persistable> one = tangle.getFirst(Transaction.class, TransactionHash.class);
            while (one != null && one.low != null) {

                TransactionViewModel model = new TransactionViewModel((Transaction)one.hi, (TransactionHash)one.low);

                Hash tag = model.getTagValue();
                String tagStr = Converter.trytesToAscii(Converter.trytes(tag.trits()));
                String type = tagStr.substring(8, 10);

                if(type.equals("TX")) {
                    byte[] trits = model.getSignature();
                    String trytes = Converter.trytes(trits);

                    String bytes = Converter.trytesToAscii(trytes);
                    JsonReader jsonReader = new JsonReader(new StringReader(bytes));
                    jsonReader.setLenient(true);

                    BatchTxns batchTxns = new Gson().fromJson(jsonReader, BatchTxns.class);

                    for (Txn txn: batchTxns.txn_content) {
                        transactions.add(txn);
                        putIndex(txn, (TransactionHash)one.low);
                        addTxn(txn);
                    }

                }

                one = tangle.next(Transaction.class, one.low);
            }

        }catch (NullPointerException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    static class RawTxn {
        String from;
        String to;
        long amnt;

        public void setFrom(String from) {
            this.from = from;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public void setAmnt(long amnt) {
            this.amnt = amnt;
        }

        public String toString() {
            return from + ":" + to + ":" +amnt+"\n";
        }
    }

    public void createTmpStorageForBlock(BatchTxns tmpBatch){
        List<Txn> newList = tmpBatch.txn_content.stream().collect(Collectors.toList());
        tmpStorage.add(newList);
    }

    public void batchPutIndex(List<Hash> hashList) {
        if(hashList.size() != tmpStorage.size() || hashList.size() <= 1) {
            return;
        }
        int i=0;
        for(Hash h : hashList) {
            for (Txn t : tmpStorage.get(i)) {
                putIndex(t, h);
            }
            i++;
        }
        tmpStorage.clear();
    }

    public void putIndex(Txn tx, Hash blockHash) {
        txnToTangleMap.put(tx.txnHash, blockHash);
        if(tangleToTxnMap.get(blockHash) != null) {
            HashSet<Txn> s = tangleToTxnMap.get(blockHash);
            s.add(tx);
            tangleToTxnMap.put(blockHash, s);
        } else {
            HashSet<Txn> s = new HashSet<Txn>();
            s.add(tx);
            tangleToTxnMap.put(blockHash, s);
        }
    }

    public void addTxn(Txn txn) {
        transactions.add(txn);
        utxoGraph.addTxn(txn, transactions.size()-1);
    }

    public String getData() {
        String ret = "";
        if(checkConsistency()) {
            for(Hash h : tangleToTxnMap.keySet()) {
                ret += IotaUtils.abbrieviateHash(h, 4) + " : ";
                BatchTxns btx = new BatchTxns();
                for(Txn tx : tangleToTxnMap.get(h)) {
                    btx.addTxn(tx);
                }
                ret += btx.getString(btx) + "\n";
            }
        }
        return ret;
    }

    public boolean checkConsistency() {
        try {
            // forward check
            for(Hash h : tangleToTxnMap.keySet()) {
                TransactionViewModel model = TransactionViewModel.find(tangle, h.bytes());
                String sig = Converter.trytes(model.getSignature());
                String txnsStr = Converter.trytesToAscii(sig);

                JSONObject jo = new JSONObject(txnsStr);

                JSONArray jsonArray = (JSONArray) jo.get("txn_content");

                for(Txn t : tangleToTxnMap.get(h)) {
                    boolean found = false;
                    for (Object object : jsonArray) {
                        Txn jo1 = new Gson().fromJson(object.toString(), Txn.class);
                        String str1 = new Gson().toJson(jo1);
                        String str2 = new Gson().toJson(t);
                        if(str1.equals(str2)) {
                            found = true;
                            break;
                        }
                    }
                    if(!found) {
                        return false;
                    }
                }
            }
            // TODO backward check
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Txn> readFromStr(String txnsStr){

        List<RawTxn> transactionList = new ArrayList<>();

        RawTxn tx = new Gson().fromJson(txnsStr, RawTxn.class);
        transactionList.add(tx);

        return constructTxnsFromRawTxns(transactionList);
    }

    public void readFromLines(String[] lines){
        List<RawTxn> transactionList = new ArrayList<>();

        for (String line:lines) {
            RawTxn tx = new Gson().fromJson(line, RawTxn.class);
            transactionList.add(tx);
        }

        constructTxnsFromRawTxns(transactionList);
    }

    public void readFromIPFSAddr(String ipfsAddr) throws IOException{
        List<RawTxn> rawTxnsList = readRawTxnInfoFromIPFS(ipfsAddr);
        constructTxnsFromRawTxns(rawTxnsList);
    }

    public static Txn genesis() {
        List<TxnOut> txnOutList = new ArrayList<>();
        TxnOut txOut = new TxnOut();
        txOut.amount = 1000000000;  //just for testing
        txOut.userAccount = "A";  //just for testing
        txnOutList.add(txOut);

        Txn newTxn = new Txn();
        newTxn.inputs = new ArrayList<TxnIn>();
        TxnIn in = new TxnIn();
        newTxn.inputs.add(in);
        newTxn.outputs = txnOutList;

        newTxn.txnHash = generateHash(new Gson().toJson(newTxn));

        return newTxn;
    }

    public void init() {
        transactions = new ArrayList<>();

        addTxn(genesis());
    }

    public Txn getLast() {
        return transactions.get(transactions.size()-1);
    }

    public Txn popLast() {
        Txn ret = transactions.get(transactions.size()-1);
        transactions.remove(transactions.size()-1);
        return ret;
    }

    private List<Txn> constructTxnsFromRawTxns(List<RawTxn> rawTxns) {
        int size = transactions.size();
        List<Txn> ret = new ArrayList<>();

        for (RawTxn txn: rawTxns) {
            Txn tx = doCreateRawTxn(txn);
            if (tx == null){
                break;
            }
            ret.add(tx);
        }
        return ret;
    }

    private List<RawTxn> readRawTxnInfoFromIPFS (String ipfsAddr) throws IOException {

        List<RawTxn> transactionList = new ArrayList<>();

        IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");

        Multihash filePointer = Multihash.fromBase58(ipfsAddr);
        byte[] fileContents = ipfs.cat(filePointer);

        String str = new String(fileContents);

        String[] lines = str.split(System.getProperty("line.separator"));

        for (String line:lines) {
            RawTxn tx = new Gson().fromJson(line, RawTxn.class);
            transactionList.add(tx);
        }

        return transactionList;
    }


    private Txn doCreateRawTxn(RawTxn txn) {
        String fromAddr = txn.from;
        String toAddr = txn.to;
        long total = 0;

        List<TxnIn> txnInList = new ArrayList<>();

        Set<Integer> unspendForAccount = utxoGraph.findUnspentTxnsForAccount(fromAddr);

        for (Integer idx : unspendForAccount){
            List<TxnOut> txnOutList = transactions.get(idx).outputs;
            for (int j = 0; j < txnOutList.size(); j++) {
                TxnOut txnOut = txnOutList.get(j);
                if (txnOut.userAccount.equals(fromAddr)){
                    TxnIn txnIn = new TxnIn();
                    txnIn.userAccount = fromAddr;
                    txnIn.txnHash = transactions.get(idx).txnHash;
                    txnIn.idx = j;

                    txnInList.add(txnIn);
                    total += txnOut.amount;
                    if (total >= txn.amnt) {
                        break;
                    }
                }
            }
            if (total >= txn.amnt) {
                break;
            }
        }

        if (txnInList.size() == 0 || total < txn.amnt) {
            // TODO: it will print out the value of 'from' and the 'transfer value', will it be ok?
            log.error("Error, {} have {} token, but want to spend {}.", txn.from, total, txn.amnt);
            return null;
        }

        Txn newTxn = new Txn();
        newTxn.inputs = txnInList;

        List<TxnOut> txnOutList = new ArrayList<>();

        TxnOut toTxOut = new TxnOut();
        toTxOut.amount = txn.amnt;
        toTxOut.userAccount = toAddr;
        txnOutList.add(toTxOut);

        if (total > txn.amnt) {
            TxnOut fromTxOut = new TxnOut();
            fromTxOut.amount = total - txn.amnt;
            fromTxOut.userAccount = fromAddr;
            txnOutList.add(fromTxOut);
        }

        newTxn.outputs = txnOutList;
        newTxn.txnHash = generateHash(new Gson().toJson(newTxn));

        return newTxn;
    }

    public static String generateHash(String txnStr) {
        String trytes = Converter.asciiToTrytes(txnStr);

        byte[] trits = Converter.allocateTritsForTrytes(trytes.length());
        Converter.trits(trytes, trits, 0);

        // The length of inputs to Sponge needs to be a multiple of 'HASH_LENGTH'
        if (trits.length % Curl.HASH_LENGTH != 0) {
            byte[] extend = new byte[(trits.length / Curl.HASH_LENGTH + 1) * Curl.HASH_LENGTH];
            System.arraycopy(trits, 0, extend, 0, trits.length);
            trits = extend;
        }

        Sponge k = SpongeFactory.create(SpongeFactory.Mode.KERL);
        k.absorb(trits, 0, trits.length);

        byte[] hashValue = new byte[Curl.HASH_LENGTH];
        k.squeeze(hashValue, 0, hashValue.length);

        String hash = Converter.trytes(hashValue);
        return hash;
    }

    public long getBalance(String account) {
        LocalInMemoryGraphProvider provider = (LocalInMemoryGraphProvider)tangle.getPersistenceProvider("LOCAL_GRAPH");
        provider.computeScore();
        List<Hash> totalTopOrders = provider.totalTopOrder();
        //log.debug("all txs = {}", transactions.toString());
        utxoGraph.markDoubleSpend(totalTopOrders, txnToTangleMap);
        //
        Set<String> visisted = new HashSet<>();


        long total = 0;

        for (int i = 0; i < transactions.size(); i++) {
            Txn transaction = transactions.get(i);
            if(visisted.contains(transaction.txnHash)) {
                continue; //FIXME this is a problem
            }
            List<TxnOut> txnOutList = transaction.outputs;
            for (int j = 0; j < txnOutList.size(); j++) {
                TxnOut txnOut = txnOutList.get(j);
                String key = transaction.txnHash + ":" + String.valueOf(j) + "," + txnOut.userAccount;
                if (txnOut.userAccount.equals(account) && !utxoGraph.isSpent(key) && !utxoGraph.isDoubleSpend(key)) {
                    total += txnOut.amount;
                }
            }
            visisted.add(transaction.txnHash);
        }
        return total;
    }

    public String getUTXOGraph(String type) {
        LocalInMemoryGraphProvider provider = (LocalInMemoryGraphProvider)tangle.getPersistenceProvider("LOCAL_GRAPH");
        List<Hash> totalTopOrders = provider.totalTopOrder();
        //log.debug("all txs = {}", transactions.toString());
        utxoGraph.markDoubleSpend(totalTopOrders, txnToTangleMap);
        return utxoGraph.printGraph(utxoGraph.outGraph, type, null);
    }

    private void checkAllBalance(UTXOGraph graph) {
        long tot = 0;
        Set<String> spend = new HashSet<>();
        for (int i = 0; i < transactions.size(); i++) {
            Txn transaction = transactions.get(i);
            List<TxnOut> txnOutList = transaction.outputs;
            for (int j = 0; j < txnOutList.size(); j++) {
                TxnOut txnOut = txnOutList.get(j);
                String key = transaction.txnHash + ":" + String.valueOf(j) + "," + txnOut.userAccount;
                if (!graph.isSpent(key) && !graph.isDoubleSpend(key)) {
                    tot += txnOut.amount;
                    spend.add(key);
                }
            }
        }
        if(tot != 1000000000) {
            System.out.println("[total] " + tot);
            graph.printGraph(graph.outGraph, "graph.dot", spend);
        }
    }
}
