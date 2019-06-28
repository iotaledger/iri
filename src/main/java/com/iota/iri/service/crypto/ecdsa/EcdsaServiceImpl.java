package com.iota.iri.service.crypto.ecdsa;

import com.iota.iri.service.crypto.CryptoService;
import com.iri.utils.crypto.ellipticcurve.EcdsaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class EcdsaServiceImpl implements CryptoService {
    private static final Logger log = LoggerFactory.getLogger(EcdsaServiceImpl.class);

    @Override
    public String sign(String privateKey, String message) {

        return null;
    }

    @Override
    public boolean verify(String sign, String address, String message) {
        try {
            EcdsaUtils.ValidRes res = EcdsaUtils.verifyMessage(sign, message, address);
            if (!res.verifyResult()){
                log.error(String.format("Signatire verification failed for : %s", res.errMessage()));
            }
            return res.verifyResult();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
