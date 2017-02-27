package com.iota.iri.hash.keys;

/**
 * Created by paul on 2/15/17.
 */
public class TupleKeyArray {
    public TupleArray[] keys;
    //public TupleKeyArray first;
    //public TupleKeyArray second;
    public static TupleKeyArray create(int size) {
        return TupleKeyArray.create(new TupleArray[size]);
    }
    public static TupleKeyArray create(TupleArray[] val) {
        TupleKeyArray hash = new TupleKeyArray();
        hash.keys = val;
        return hash;
    }
}
