package com.iota.iri.model;


/**
 * Created by paul on 3/8/17 for iri.
 */
public class Flag {
    public Hash hash;
    public byte[] status = new byte[]{0};

    public Flag() {}
    public Flag(Hash hashBytes) {
        hash = hashBytes;
    }
}
