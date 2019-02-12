package com.iota.iri.pluggables.utxo;

import java.util.*;

import com.alibaba.fastjson.annotation.JSONField;
import com.iota.iri.model.*;

public class Transaction {

    @JSONField(serialize=false)
    Hash txnHash;

    List<TransactionIn> inputs;

    List<TransactionOut> outputs;

    public List<TransactionIn> getInputs() {
        return inputs;
    }

    public List<TransactionOut> getOutputs() {
        return outputs;
    }
}
