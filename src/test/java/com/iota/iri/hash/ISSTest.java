package com.iota.iri.hash;

import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;

import static org.junit.Assert.*;

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
        final int startIndex, numberOfKeys, keySize, levels;
        seedStr = "MYSEEDSARETHEBEST9SEEDSWHODONTUSEMYSEEDARESADALLSEEDSSHOULDBEZEROLENGTHORGREATER9";
        seed = Converter.trits(seedStr);
        startIndex = 0;
        numberOfKeys = 102; //randomly chosen
        keySize = 27*243;//81;
        levels = 31 - Integer.numberOfLeadingZeros(numberOfKeys);
        MerkleHash merkleRoot = ISS.merkleTree(seed, startIndex, numberOfKeys, keySize);
        MerkleHash child = merkleRoot;
        while(child != null) {
            if(child.first != null) {
                int[] curlHash = new int[keySize];
                Curl curl = new Curl();
                curl.absorb(ArrayUtils.addAll(child.first.value, child.second.value), 0, keySize*2);
                curl.squeeze(curlHash, 0, keySize);
                assertArrayEquals(curlHash, child.value);
            }
            child = child.first;
        }
    }
}