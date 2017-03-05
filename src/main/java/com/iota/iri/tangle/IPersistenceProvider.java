package com.iota.iri.tangle;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Created by paul on 3/2/17 for iri.
 */
public interface IPersistenceProvider {
    void init() throws Exception;
    void shutdown();
    boolean save(Object o);
    boolean get(Object c, Object key) throws Exception;

    void setColumns(Map<Class<?>, String> modelPrimaryKey, Map<Class<?>, Set<String>> modelIndices, Map<Class<?>, Set<String>> modelStoredItems);

    boolean query(Object model, String index, Object value) throws Exception;
    /*
    IStorageQuery<T> find(int index);
    interface IStorageQuery<T> {
        Function<String, byte[]> where(String s);
    }
    */
}
