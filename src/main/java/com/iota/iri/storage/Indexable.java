package com.iota.iri.storage;

import java.io.Serializable;

/**
 * Created by paul on 5/6/17.
 */
public interface Indexable extends Comparable<Indexable>, Serializable {
    byte[] bytes();
    void read(byte[] bytes);
    Indexable incremented();
    Indexable decremented();
}
