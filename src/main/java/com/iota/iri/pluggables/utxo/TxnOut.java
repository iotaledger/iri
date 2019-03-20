package com.iota.iri.pluggables.utxo;

public class TxnOut {
    long amount;
    String userAccount;

    public long getAmount() {
        return amount;
    }

    public String getUserAccount() {
        return userAccount;
    }

    public String toString() {
        return "account: " + userAccount + ", amount: " + amount;
    }
}
