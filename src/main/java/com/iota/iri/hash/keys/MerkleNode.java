package com.iota.iri.hash.keys;

/**
 * Created by paul on 2/22/17.
 */
public class MerkleNode {
    public int[] value;
    public MerkleNode[] children;
    public static MerkleNode create(int[] val) {
        MerkleNode node = new MerkleNode();
        node.value = val;
        return node;
    }
    public static MerkleNode create(int[] val, MerkleNode[] children) {
        MerkleNode node = MerkleNode.create(val);
        node.children = children;
        return node;
    }
}
