package com.iota.iri.utils;

import org.junit.Assert;
import org.junit.Test;

public class ConverterTest {
    @Test
    public void testFromTextToTrites() {
        String trytes = "LBCBKBSBCBKBKBRBCCAC9";
        try {
            String res = Converter.trytesToAscii(trytes);
            Assert.assertEquals(res, "B9AI9AAHTR\0");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}