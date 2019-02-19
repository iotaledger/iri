package com.iota.iri.pluggables.utxo;

import java.util.*;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iota.iri.model.*;
import com.iota.iri.utils.Converter;

public class Txn {

    @JSONField(serialize=false)
    Hash txnHash;

    List<TxnIn> inputs;

    List<TxnOut> outputs;

    public List<TxnIn> getInputs() {
        return inputs;
    }

    public List<TxnOut> getOutputs() {
        return outputs;
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
}
