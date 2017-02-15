package com.iota.iri.hash;

import com.iota.iri.utils.Converter;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by paul on 2/13/17.
 */
public class ISSTests {
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

}