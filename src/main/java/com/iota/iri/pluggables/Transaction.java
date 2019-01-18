package com.iota.iri.pluggable.utxo;

import java.util.*;
import com.iota.iri.model.*;

class Transaction {

    Hash txnHash;

    List<TransactionIn> inputs;
    List<TransactionOut> outputs;
}
