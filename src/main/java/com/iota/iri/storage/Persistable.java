package com.iota.iri.storage;

import java.io.Serializable;

/**
 * Any object that can be serialized and stored in the database.
 *
 * @see Indexable
 */
public interface Persistable extends Serializable {
    /**
     * Serializes the core fields of the object to bytes that can be stored in a database. Core fields are all the
     * fields that represent the core data required to construct this object.
     *
     * @return the serialized bytes that represents the core original object
     */
    byte[] bytes();

    /**
     * Recreates the core state (populates the fields) of the object by deserializing {@code bytes}.
     *
     * @param bytes the serialized version of the object
     */
    void read(byte[] bytes);

    /**
     * Serializes fields that usually contain data about the object that the node itself calculates
     * and is not passed to different nodes. This data is stored in a different column family/table then
     * the data serialized by {@link #bytes()}.
     *
     * @return the serialized bytes that represents the metadata of the object
     */
    byte[] metadata();

    /**
     * Recreates the metadata (populates the relevant fields) of the object by deserializing {@code bytes}.
     *
     * @param bytes the serialized bytes that represents the metadata of the object
     */
    void readMetadata(byte[] bytes);

    /**
     * Specifies whether the object should be appended to an existing key/index or should replace the old
     * indexed object.
     *
     * @return <tt>true</tt> if we should add the object to a list for an existing key. <tt>false</tt> if we should
     * false if we should replace the old value
     */
    boolean merge();
}
