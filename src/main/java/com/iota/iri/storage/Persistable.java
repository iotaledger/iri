package com.iota.iri.storage;

import javax.naming.OperationNotSupportedException;
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
     * Specifies whether two objects of the same type can be merged and their merged result used as a single object.
     * For storing in a persistence provider this means we can append items to an index and
     * for retrieving this means we can merge the results of multiple storage providers
     *
     * @return <tt>true</tt> if we mergeable <tt>false</tt> if not mergeable
     */
    boolean canMerge();

    /**
     * Merges source object into this object.
     * @param source the persistable that will be merged into the called persistable
     * @return the updated persistable, which contains both. Or {@code null} if the persistable cannot be merged
     * @throws OperationNotSupportedException when the persistable called does not support merging. Call canMerge first.
     */
    Persistable mergeInto(Persistable source) throws OperationNotSupportedException;


    /**
     * Determines whether the data exists. This method is needed because non {@code null} data doesn't mean that the data doesn't exist.
     *
     * @return true if data exists , else returns false
     */
    boolean exists();
}
