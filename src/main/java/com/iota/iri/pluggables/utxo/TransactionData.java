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
import com.iota.iri.utils.Pair;
import io.ipfs.api.IPFS;
import io.ipfs.multihash.Multihash;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

import  com.iota.iri.utils.Converter;

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
        int i=hashList.size()-1;
        for(Hash h : hashList) {
            for (Txn t : tmpStorage.get(i)) {
                putIndex(t, h);
            }
            i--;
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

    public void readFromStr(String txnsStr){

        List<RawTxn> transactionList = new ArrayList<>();

        RawTxn tx = new Gson().fromJson(txnsStr, RawTxn.class);
        transactionList.add(tx);

        constructTxnsFromRawTxns(transactionList);
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


    public void init() {
        transactions = new ArrayList<>();

        List<TxnOut> txnOutList = new ArrayList<>();
        TxnOut txOut = new TxnOut();
        txOut.amount = 10000;  //just for testing
        txOut.userAccount = "A";  //just for testing
        txnOutList.add(txOut);

        Txn newTxn = new Txn();
        newTxn.inputs = null;
        newTxn.outputs = txnOutList;

        newTxn.txnHash = generateHash(new Gson().toJson(newTxn));

        transactions.add(newTxn);
    }

    public Txn getLast() {
        return transactions.get(transactions.size()-1);
    }

    private boolean constructTxnsFromRawTxns(List<RawTxn> rawTxns) {
        int size = transactions.size();
        boolean undoFlag = false;

        for (RawTxn txn: rawTxns) {
            boolean flag = doStoreRawTxn(txn);
            if (flag == false){
                undoFlag = true;
                break;
            }
        }

        if (undoFlag == true){
            int undoSize = transactions.size();
            if (undoSize > size) {
                for (int i = size; i < undoSize; i++) {
                    transactions.remove(i);
                }
            }
            return false;
        }

        return true;
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


    private boolean doStoreRawTxn(RawTxn txn) {
        String fromAddr = txn.from;
        String toAddr = txn.to;
        long total = 0;

        List<TxnIn> txnInList = new ArrayList<>();

        // TODO: find unspent utxo more quickly.
        for (int i = transactions.size() - 1; i >= 0; i--){

            List<TxnOut> txnOutList = transactions.get(i).outputs;
            for (int j = 0; j < txnOutList.size(); j++) {
                TxnOut txnOut = txnOutList.get(j);
                if (txnOut.userAccount.equals(fromAddr)){

                    boolean jumpFlag = false;

                    // TODO: check utxo whether or not being spent more quickly.
                    out:
                    for (int k = transactions.size() - 1; k > 0; k--){
                        for (TxnIn tempTxnIn: transactions.get(k).inputs) {
                            if (tempTxnIn.txnHash.equals(transactions.get(i).txnHash) && tempTxnIn.idx == j){
                                jumpFlag = true; // already spend
                                break out;
                            }
                        }
                    }
                    if (jumpFlag){
                        continue;
                    }

                    TxnIn txnIn = new TxnIn();
                    txnIn.userAccount = fromAddr;
                    txnIn.txnHash = transactions.get(i).txnHash;
                    txnIn.idx = j;

                    txnInList.add(txnIn);
                    total += txnOut.amount;
                    if (total >= txn.amnt) {
                        break;
                    }
                }
            }
        }

        if (txnInList.size() == 0 || total < txn.amnt) {
            // TODO: it will print out the value of 'from' and the 'transfer value', will it be ok?
            log.error("Error, {} have {} token, but want to spend {}.", txn.from, total, txn.amnt);
            return false;
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

        transactions.add(newTxn);
        return true;
    }

    private String generateHash(String txnStr) {
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
        long total = 0;
        // TODO: find unspent utxo more quickly.
        for (int i = transactions.size() - 1; i >= 0; i--){
            List<TxnOut> txnOutList = transactions.get(i).outputs;
            for (int j = 0; j < txnOutList.size(); j++) {
                TxnOut txnOut = txnOutList.get(j);
                if (txnOut.userAccount.equals(account)){
                    boolean jumpFlag = false;
                    // TODO: check utxo whether or not being spent more quickly.
                    out:
                    for (int k = transactions.size() - 1; k > 0; k--){
                        for (TxnIn tempTxnIn: transactions.get(k).inputs) {
                            if (tempTxnIn.txnHash.equals(transactions.get(i).txnHash) && tempTxnIn.idx == j){
                                jumpFlag = true; // already spend
                                break out;
                            }
                        }
                    }
                    if (jumpFlag){
                        continue;
                    }

                    total += txnOut.amount;
                }
            }
        }

        return total;
    }
}
