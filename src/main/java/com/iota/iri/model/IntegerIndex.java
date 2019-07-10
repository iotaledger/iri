package com.iota.iri.model;

import com.iota.iri.storage.Indexable;
import com.iota.iri.utils.Serializer;

/**
 An integer key that indexes {@link Persistable} objects in the database.
 */
public class IntegerIndex implements Indexable{

    /**The internally stored index value*/
    int value;

    /**
     * Constructor for storing index of persistable
     * @param value The index of the represented persistable
     */
    public IntegerIndex(int value) {
        this.value = value;
    }

    public IntegerIndex() {}

    /**@return The index of the persistable */
    public int getValue() {
        return value;
    }

    @Override
    public byte[] bytes() {
        return Serializer.serialize(value);
    }

    @Override
    public void read(byte[] bytes) {
        this.value = Serializer.getInteger(bytes);
    }

    /**Creates a new {@link #IntegerIndex(int)} with an incremented index value*/
    @Override
    public Indexable incremented() {
        return new IntegerIndex(value + 1);
    }

    /**Creates a new {@link #IntegerIndex(int)} with a decremented index value*/
    @Override
    public Indexable decremented() {
        return new IntegerIndex(value - 1);
    }

    @Override
    public int compareTo(Indexable o) {
        IntegerIndex i = new IntegerIndex(Serializer.getInteger(o.bytes()));
        return value - ((IntegerIndex) o).value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof IntegerIndex)) {
            return false;
        }

        return ((IntegerIndex) obj).value == value;
    }
}
