package com.iota.iri.pluggables.utxo;

import com.iota.iri.model.*;

public class TxnIn {
    String txnHash;
    int idx;
    String userAccount;

    public TxnIn() {
        txnHash = "";
        idx = 0;
        userAccount = "";
    }

    public String getTxnHash() {
        return txnHash;
    }

    public int getIdx() {
        return idx;
    }

    public String getUserAccount() {
        return userAccount;
    }

    public String toString() {
        return "txnHash: " + txnHash + ", idx: " + idx + ", account: " + userAccount;
    }
}
