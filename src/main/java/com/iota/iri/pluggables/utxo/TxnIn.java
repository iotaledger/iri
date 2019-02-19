package com.iota.iri.pluggables.utxo;

import com.iota.iri.model.*;

public class TxnIn {
    Hash txnHash;
    int idx;
    String userAccount;

    public Hash getTxnHash() {
        return txnHash;
    }

    public int getIdx() {
        return idx;
    }

    public String getUserAccount() {
        return userAccount;
    }
}
