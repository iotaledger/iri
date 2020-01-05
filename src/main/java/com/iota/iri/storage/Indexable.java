package com.iota.iri.storage;

import java.io.Serializable;

/**
 * An object that is used as a key/index in a database to give an ID to a {@link Persistable}.
 */
public interface Indexable extends Comparable<Indexable>, Serializable {

    /**
     * Serializes the object to bytes in order to be stored in the db.
     *
     * @return the serialized bytes of the key
     */
    byte[] bytes();

    /**
     * Deserializes the index from bytes and recreates the object.
     *
     * @param bytes the serialized bytes of the key
     */
    void read(byte[] bytes);

    /////////////////////////////////Not In Use////////////////////////////////////////////

    Indexable incremented();
    Indexable decremented();
}
