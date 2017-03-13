package com.iota.iri.service.tangle;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Created by paul on 3/2/17 for iri.
 */
public interface IPersistenceProvider {
    void init() throws Exception;
    void init(String path) throws Exception;
    void shutdown();
    boolean save(Object o) throws Exception;
    void delete(Object o) throws Exception;
    Object get(Class<?> modelClass, Object key) throws Exception;

    void setColumns(Map<Class<?>, Field> modelPrimaryKey, Map<Class<?>, Map<String, ModelFieldInfo>> modelItems);

    Object query(Class<?> model, String index, Object value, int keyLength) throws Exception;

    boolean update(Object model, String item, Object value) throws Exception;

    Object[] queryMany(Class<?> modelClass, String index, Object key, int keyLength) throws Exception;


    boolean setTransientHandle(Class<?> model, Object uuid);
    void dropTransientHandle(Object handle) throws Exception;
    boolean save(Object handle, Object model) throws Exception;
    boolean mayExist(Object handle, Object key) throws Exception;
    boolean mayExist(Class<?> model, Object key) throws Exception;
    Object get(Object handle, Class<?> model, Object key) throws Exception;
    void deleteTransientObject(Object uuid, Object key) throws Exception;

    Object latest(Class<?> model) throws  Exception;
}
