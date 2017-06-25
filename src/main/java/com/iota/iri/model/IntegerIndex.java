package com.iota.iri.model;

import com.iota.iri.storage.Indexable;
import com.iota.iri.utils.Serializer;

/**
 * Created by paul on 5/6/17.
 */
public class IntegerIndex implements Indexable{
    int value;
    public IntegerIndex(int value) {
        this.value = value;
    }

    public IntegerIndex() {}

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

    @Override
    public int compareTo(Indexable o) {
        return value - ((IntegerIndex) o).value;
    }
}
