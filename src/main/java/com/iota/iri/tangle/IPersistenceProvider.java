package com.iota.iri.tangle;

import com.iota.iri.model.Transaction;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * Created by paul on 3/2/17 for iri.
 */
public interface IPersistenceProvider {
    void init() throws Exception;
    void shutdown();
    boolean save(Object o) throws Exception;
    Object get(Class<?> modelClass, Object key) throws Exception;

    void setColumns(Map<Class<?>, Field> modelPrimaryKey, Map<Class<?>, Map<String, ModelFieldInfo>> modelItems);

    Object query(Class<?> model, String index, Object value, int keyLength) throws Exception;

    boolean update(Object model, String item, Object value) throws Exception;

    Object[] queryMany(Class<?> modelClass, String index, Object key, int keyLength) throws Exception;
    /*
    interface QueryBuilder {
        QueryBuilder where(String index, Object value);
        QueryBuilder and();
        Object execute();
    }
    */
}
