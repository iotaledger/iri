package com.iota.iri.hash.keys;

/**
 * Created by paul on 2/22/17.
 */
public class TupleArray {
    public Tuple[] keys;
    public static TupleArray create(Tuple[] keys) {
        TupleArray tupleArray = new TupleArray();
        tupleArray.keys = keys;
        return tupleArray;
    }
}
