package com.iota.iri.service.tangle;

import com.iota.iri.model.*;

/**
 * Created by paul on 3/2/17 for iri.
 */
public interface IPersistenceProvider {
    void init() throws Exception;
    void shutdown();
    boolean save(Object o) throws Exception;
    void delete(Object o) throws Exception;

    boolean update(Object model, String item) throws Exception;

    boolean save(int handle, Object model) throws Exception;
    boolean mayExist(int handle, Hash key) throws Exception;
    boolean exists(Class<?> model, Hash key) throws Exception;
    Object get(int handle, Class<?> model, Hash key) throws Exception;
    void copyTransientList(int sourceId, int destId) throws Exception;

    Object latest(Class<?> model) throws  Exception;

    Object[] getKeys(Class<?> modelClass) throws Exception;

    boolean get(Object model) throws Exception;

    boolean mayExist(Object model) throws Exception;

    void flush(Class<?> modelClass) throws Exception;

    long count(Class<?> model) throws Exception;

    boolean setTransientFlagHandle(int uuid) throws Exception;

    void flushTagRange(int id) throws Exception;

    long count(int transientId) throws Exception;
}
