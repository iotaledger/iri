package com.iota.iri.utils;

import org.junit.Assert;
import org.junit.Test;

public class ConverterTest {

    @Test
    public void testFromTextToTrites() {
        String trytes = "LBCBKBSBCBKBKBRBCCAC9999999999999999999999999999999999999999999999999999999999999";
        try {
            String res = Converter.trytesToAscii(trytes);
            Assert.assertEquals(res, "B9AI9AAHTR");
        } catch(Exception e) {
            e.printStackTrace();
        }

        trytes = "QC";
        try {
            String res = Converter.trytesToAscii(trytes);
            Assert.assertEquals(res, "b");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
