package com.iota.iri.model;

import com.iota.iri.storage.Indexable;
import com.iota.iri.utils.Serializer;

/**
 * Created by paul on 5/6/17.
 */
public class IntegerIndex implements Indexable{
    long value;
    public IntegerIndex(long value) {
        this.value = value;
    }

    public IntegerIndex() {}

    public long getValue() {
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
    public Indexable incremented() {
        return new IntegerIndex(value + 1);
    }

    @Override
    public Indexable decremented() {
        return new IntegerIndex(value - 1);
    }

    @Override
    public int compareTo(Indexable o) {
        return ((Long) value).compareTo(new IntegerIndex(Serializer.getInteger(o.bytes())).getValue());
    }
}
