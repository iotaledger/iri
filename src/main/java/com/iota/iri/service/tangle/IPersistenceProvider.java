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

    boolean exists(Class<?> model, Hash key) throws Exception;

    Object latest(Class<?> model) throws  Exception;

    Object[] getKeys(Class<?> modelClass) throws Exception;

    boolean get(Object model) throws Exception;

    boolean mayExist(Object model) throws Exception;

    long count(Class<?> model) throws Exception;

    Object[] scanForKeys(Class<?> modelClass, byte[] value);
}
