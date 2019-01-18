package com.iota.iri.pluggable.utxo;

import java.util.*;
import com.iota.iri.storage.Tangle;

public class TransactionData {

    class RawTxn {
        String from;
        String to;
        long amnt;
    }

    public TransactionData(String ipfsAddr) {
        List<RawTxn> rawTxns = readRawTxnInfoFromIPFS(ipfsAddr);
    }

    private void constructTxnsFromRawTxns(List<RawTxn> rawTxns) {
        // read data from tangle and construct transactions
    }

    private List<RawTxn> readRawTxnInfoFromIPFS(String ipfsAddr) {
        return null;
    }

    Tangle tangle;
    List<Transaction> transactions;
}
