package com.iota.iri.utils;

import com.iota.iri.ellipticcurve.utils.EcdsaUtils;
import org.junit.Test;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class EcdsaUtilsTest {

    @Test
    public void testVerifyMessage(){
        String address = "14dD6ygPi5WXdwwBTt1FBZK3aD8uDem1FY";
        String message = "test message";
        String signature = "IAJCS1jD78b6miDTFfHQ1ztgh3Y3Le3klxLWRjr+WQEai6C56EtS19RmRiLIft67kMs56Oflp3OQxI99BCPKfns=";
        try {
            EcdsaUtils.ValidRes res = EcdsaUtils.verifyMessage(signature, message, address);
            assert res.verifyResult();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
