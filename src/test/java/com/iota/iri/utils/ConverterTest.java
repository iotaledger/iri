package com.iota.iri.utils;

import org.junit.Assert;
import org.junit.Test;

public class ConverterTest {
    @Test
    public void testTrytesToAscii() {
        String trytes = "OBGCZBOBMBCCOBNBNCACOBBCDCVBCC9";
        Assert.assertEquals(Converter.trytesToAscii(trytes), "EXPECTED_RESULT\0");

        trytes = "RBOBVBVBYB999999999";
        Assert.assertEquals(Converter.trytesToAscii(trytes), "HELLO\0\0\0\0\0");
    }

}