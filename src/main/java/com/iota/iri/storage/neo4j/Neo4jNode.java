package com.iota.iri.storage.neo4j;

import com.iota.iri.storage.PersistenceProvider;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;

import org.apache.commons.lang3.Conversion;

import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.model.persistables.*;

import java.util.*;

import static org.neo4j.driver.v1.Values.parameters;

import com.iota.iri.hash.Curl;

public class Neo4jNode
{
    // tag name will be txn, 
    public String txnNodeHash; // this is node name
    public String addressHash;
    public String bundleHash;
    public String trunkHash;
    public String branchHash;
    public String obsoleteTagHash;
    public String tagHash;
    public String txnEncode;

    public Neo4jNode(List<Pair<Indexable, Persistable>> models)
    {
        boolean isTrunkSet = false;
        for (Pair<Indexable, Persistable> entry : models) {
            Indexable key = entry.low;
            Persistable value = entry.hi;

            byte[] dest = new byte[Curl.HASH_LENGTH];
            Converter.getTrits(key.bytes(), dest);
            String keyStr = Converter.trytes(dest, 0, dest.length);

            byte[] dest1 = new byte[Curl.HASH_LENGTH];
            Converter.getTrits(value.bytes(), dest1);
            String valStr = Converter.trytes(dest1, 0, dest1.length);

            if(value.getClass().equals(Address.class)) {
                addressHash = keyStr;
                txnNodeHash = valStr;
            } else if(value.getClass().equals(Bundle.class)) {
                bundleHash = keyStr;
            } else if(value.getClass().equals(Approvee.class)) {
                if(isTrunkSet) {
                    trunkHash = keyStr;
                } else {
                    branchHash = keyStr;
                }
            } else if(value.getClass().equals(ObsoleteTag.class)) {
                obsoleteTagHash = keyStr;
            } else if (value.getClass().equals(Tag.class)) {
                tagHash = keyStr;
            } else if(value.getClass().equals(Transaction.class)) {
                txnEncode = keyStr;
            }
        }
    }
}