package com.iota.iri.storage;

import com.iota.iri.model.*;

import java.util.Map;
import java.util.Set;

/**
 * Created by paul on 3/2/17 for iri.
 */
public interface PersistenceProvider {

    void init() throws Exception;
    boolean isAvailable();
    void shutdown();
    boolean save(Persistable model, Indexable index) throws Exception;
    void delete(Class<?> model, Indexable  index) throws Exception;

    boolean update(Persistable model, Indexable index, String item) throws Exception;

    boolean exists(Class<?> model, Indexable key) throws Exception;

    Persistable latest(Class<?> model) throws Exception;

    Set<Indexable> keysWithMissingReferences(Class<?> modelClass) throws Exception;

    Persistable get(Class<?> model, Indexable index) throws Exception;

    boolean mayExist(Class<?> model, Indexable index) throws Exception;

    long count(Class<?> model) throws Exception;

    Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value);

    Persistable seek(Class<?> model, byte[] key) throws Exception;

    Persistable next(Class<?> model, Indexable index) throws Exception;
    Persistable previous(Class<?> model, Indexable index) throws Exception;

    Persistable first(Class<?> model) throws Exception;

    //boolean merge(Persistable model, Indexable index) throws Exception;

    boolean saveBatch(Map<Indexable, Persistable> models) throws Exception;
}
