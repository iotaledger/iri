package com.iota.iri.model;

/**
 * Created by paul on 3/8/17 for iri.
 */
public class Tip {
    public Hash hash;
    public byte[] status = new byte[]{0};

    public Tip(Hash hashBytes) {
        this.hash = hashBytes;
    }
}
