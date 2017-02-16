package com.iota.iri.hash;

/**
 * Created by paul on 2/15/17.
 */
public class MerkleHash {
    public int[] value;
    public MerkleHash first;
    public MerkleHash second;
    public static MerkleHash create(int[] val) {
        MerkleHash hash = new MerkleHash();
        hash.value = val;
        return hash;
    }
}
