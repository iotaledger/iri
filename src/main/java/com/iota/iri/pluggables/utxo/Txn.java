package com.iota.iri.pluggables.utxo;

import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iota.iri.model.*;
import com.iota.iri.utils.Converter;

public class Txn {

    String txnHash;

    List<TxnIn> inputs;

    List<TxnOut> outputs;

    public List<TxnIn> getInputs() {
        return inputs;
    }

    public List<TxnOut> getOutputs() {
        return outputs;
    }

    public String getTxnHash() {
        return txnHash;
    }

    public int getTryteStringLen(Txn txn) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String str = mapper.writeValueAsString(txn);
            String trytes = Converter.asciiToTrytes(str);
            if (trytes != null) {
                return trytes.length();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("Hash: " + txnHash + "\n");
        s.append("In: \n");
        for (TxnIn in: inputs) {
            s.append("    " + in.toString() + "\n");
        }
        s.append("Out: \n");
        for (TxnOut out: outputs) {
            s.append("    " + out.toString() + "\n");
        }
        return s.toString();
    }
}
