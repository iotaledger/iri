package com.iota.iri.storage;

import javax.naming.OperationNotSupportedException;
import java.io.Serializable;

public interface Persistable extends Serializable {
    byte[] bytes();
    void read(byte[] bytes);
    byte[] metadata();
    void readMetadata(byte[] bytes);
    boolean merge();

    /**
     * Merges source object into this object.
     * @param source
     * @return
     */
    Persistable mergeInto(Persistable source) throws OperationNotSupportedException;


    /**
     * Determines whether the data exists. This method is needed because non {@code null} data doesn't mean that the data doesn't exist.
     *
     * @return true if data exists , else returns false
     */
    boolean exists();
}
