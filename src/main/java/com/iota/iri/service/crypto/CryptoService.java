package com.iota.iri.service.crypto;

public interface CryptoService {

    String sign(String privateKey, String message);

    boolean verify(String sign, String address, String message);

}
