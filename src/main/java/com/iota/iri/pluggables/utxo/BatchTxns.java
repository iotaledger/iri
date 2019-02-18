package com.iota.iri.pluggables.utxo;

import java.util.*;
import java.io.ByteArrayOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iota.iri.utils.Converter;

public class BatchTxns {
    public List<Txn> txn_content;
    public int tx_num;

    public void clear() {
        txn_content.clear();
        tx_num = 0;
    }

    public BatchTxns() {
        txn_content = new ArrayList<>();
        tx_num = 0;
    }

    public void addTxn(Txn txn) {
        txn_content.add(txn);
        tx_num += 1;
    }

    public String getString(BatchTxns txns) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonInString = mapper.writeValueAsString(txns);
            return jsonInString;
        } catch(Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public String getTryteString(BatchTxns txns) {
        String asciiString = getString(txns);
        return Converter.asciiToTrytes(asciiString);
    }

    public int getTryteStringLen(BatchTxns txns) {
        return getTryteString(txns).length();
    }
}
