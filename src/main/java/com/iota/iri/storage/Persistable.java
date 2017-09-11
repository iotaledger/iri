package com.iota.iri.storage;

import java.io.Serializable;

/**
 * Created by paul on 5/6/17.
 */
public interface Persistable extends Serializable {
    byte[] bytes();
    void read(byte[] bytes);
    byte[] metadata();
    void readMetadata(byte[] bytes);
    boolean merge();
}
