package com.iota.iri.hash;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by paul on 2/13/17.
 */
public class ISCTest {
    @Test
    public void encrypt() throws Exception {
        final String key, plain, cipher;
        key = "ABCDEFGHIJKLM9NOPQRSTUVWXYZ";
        plain = "9NOPQRSTUVWXYZABCDEFGHIJKLM";
        cipher = "ASRQYXWDCBJIHZOQSUWY9BDFHJL";
        assert(ISC.encrypt(key).apply(plain).equals(cipher));
    }

    @Test
    public void decrypt() throws Exception {
        final String key, plain, cipher;
        key = "ABCDEFGHIJKLM9NOPQRSTUVWXYZ";
        plain = "9NOPQRSTUVWXYZABCDEFGHIJKLM";
        cipher = "ASRQYXWDCBJIHZOQSUWY9BDFHJL";
        assert(ISC.decrypt(key).apply(cipher).equals(plain));
    }

}