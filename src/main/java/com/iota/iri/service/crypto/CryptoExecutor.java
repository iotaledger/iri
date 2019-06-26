package com.iota.iri.service.crypto;

import com.iota.iri.service.crypto.ecdsa.EcdsaServiceImpl;

public class CryptoExecutor {

    private static CryptoService ecdsaCryptoService = new EcdsaServiceImpl();

    public static CryptoService getCryptoInstance(){
        return getCryptoInstance("ecdsa");
    }

    public static CryptoService getCryptoInstance(String flag){
        if ("ecdsa".equals(flag)){
            return ecdsaCryptoService;
        }else{
            throw new RuntimeException("not find " + flag + " crypto service.");
        }
    }

}
