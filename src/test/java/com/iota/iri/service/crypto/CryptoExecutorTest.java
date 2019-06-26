package com.iota.iri.service.crypto;

import org.junit.Test;

public class CryptoExecutorTest {

    @Test
    public void testVerify(){
        String sig = "IPn9bbEdNUp6+bneZqE2YJbq9Hv5aNILq9E5eZoMSF3/fBX4zjeIN6fpXfGSGPrZyKfHQ/c/kTSP+NIwmyTzMfk=";
        String address = "14dD6ygPi5WXdwwBTt1FBZK3aD8uDem1FY";
        String message = "test message";
        boolean result = CryptoExecutor.getCryptoInstance().verify(sig, address, message);
        assert result;
    }

}
