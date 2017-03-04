package com.iota.iri.dataAccess;

import java.util.function.Function;

/**
 * Created by paul on 3/2/17 for iri.
 */
public interface IStorageProvider {
    void init();
    void shutdown();
    boolean save(Object o);
    byte[] load(Class c, int index, byte[] key);
    IStorageQuery<T> find(int index);
    interface IStorageQuery<T> {
        Function<String, byte[]> where(String s);
    }
}
