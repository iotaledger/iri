package com.iota.iri.storage;

import java.io.Serializable;

public interface Persistable extends Serializable {
    byte[] bytes();
    void read(byte[] bytes);
    byte[] metadata();
    void readMetadata(byte[] bytes);
    boolean merge();
    Persistable mergeTwo(Persistable nrTwo);
    //A required add because the persistence providers always return non null values and bytes() fails
    //on certain instances. So in the persistence provider there is no way to check if the persistence provider
    //actually found the object or not. This is needed if we have multiple persistence providers.
    boolean isEmpty();
}
