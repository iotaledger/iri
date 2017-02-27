package com.iota.iri.hash;

import com.iota.iri.hash.keys.MerkleNode;
import com.iota.iri.hash.keys.Tuple;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Stack;

/**
 * Created by paul on 2/13/17.
 */
public class ISSTest {
    @Test
    public void encrypt() throws Exception {
        final String key, plain, cipher;
        key = "ABCDEFGHIJKLM9NOPQRSTUVWXYZ";
        plain = "9NOPQRSTUVWXYZABCDEFGHIJKLM";
        cipher = "ASRQYXWDCBJIHZOQSUWY9BDFHJL";
        assert(Converter.trytes(ISS.encrypt(Converter.trits(key)).apply(Converter.trits(plain))).equals(cipher));
    }

    @Test
    public void decrypt() throws Exception {
        final String key, plain, cipher;
        key = "ABCDEFGHIJKLM9NOPQRSTUVWXYZ";
        plain = "9NOPQRSTUVWXYZABCDEFGHIJKLM";
        cipher = "ASRQYXWDCBJIHZOQSUWY9BDFHJL";
        assert(Converter.trytes(ISS.decrypt(Converter.trits(key)).apply(Converter.trits(cipher))).equals(plain));
    }


    @Test
    public void stateTritConversion() throws Exception {
        final String seedStr = "MYSEEDSARETHEBEST9SEEDSWHODONTUSEMYSEEDARESADALLSEEDSSHOULDBEZEROLENGTHORGREATER9";
        int[] seed = Converter.trits(seedStr);
        Tuple[] state = Converter.tuple(seed);
        int[] outSeed = Converter.trits(state);
        assert(seedStr.equals(Converter.trytes(outSeed)));
    }

    @Test
    public void merkleHash() throws Exception {
        final String seedStr;
        final int[] seed;
        int startIndex, numberOfKeys, keySize;
        long startTime, diff;
        seedStr = "MYSEEDSARETHEBEST9SEEDSWHODONTUSEMYSEEDARESADALLSEEDSSHOULDBEZEROLENGTHORGREATER9";
        seed = Converter.trits(seedStr);
        startIndex = 1;
        numberOfKeys = 1029;
        keySize = 81;
        MerkleNode merkleRoot = null;
        //for(numberOfKeys = 9; numberOfKeys < 200; numberOfKeys *= 9) {
            startTime = System.nanoTime();
            merkleRoot = ISS.merkleTree(seed, startIndex, numberOfKeys, keySize);
            diff = System.nanoTime() - startTime;
            System.out.println("Tree creation rate for "+numberOfKeys+" keys: " + (int)(numberOfKeys*1e9/diff) + " Keys/s. Time elapsed: " + diff/1e9 + "s");
        //}
        Stack<MerkleNode> parents = new Stack<MerkleNode>();
        parents.push(merkleRoot);
        MerkleNode child = parents.peek().children[0];
        while(child.children[0].children != null) {
            //System.out.println(Converter.trytes(child.value));
            parents.push(child);
            child = parents.peek().children[0];
        }
        final String fragment = "MYFRAGMEN";//"TZRNICE9BEFOREQURY";
        final int[] bundleFragment = Converter.trits(fragment);
        int[] signatureFragment = ISS.signatureFragment(bundleFragment, child.children[0].value);
        MerkleNode parent;
        int[] hash = ISS.address(ISS.digest(bundleFragment, signatureFragment));//new int[parents.peek().value.length];
        Assert.assertArrayEquals(hash, child.value);
        Curl curl = new Curl();
        int[] siblings;
        int i;
        while(!parents.empty()) {
            parent = parents.pop();
            siblings = Arrays.stream(parent.children).map(a -> a == null? MerkleNode.create(new int[Curl.HASH_LENGTH]).value: a.value).reduce((a,b) -> ArrayUtils.addAll(a, b)).orElse(new int[Curl.HASH_LENGTH*3]);
            for(i = 0; i < hash.length; i++) {
                siblings[i] = hash[i];
            }
            if(siblings.length < Curl.HASH_LENGTH*2) {
                siblings = ArrayUtils.addAll(siblings, new int[Curl.HASH_LENGTH*3 - siblings.length]);
            }
            curl.absorb(siblings, 0, hash.length*2);
            curl.squeeze(hash, 0, hash.length);
            curl.reset();
            Assert.assertArrayEquals(hash, parent.value);
        }
    }
}