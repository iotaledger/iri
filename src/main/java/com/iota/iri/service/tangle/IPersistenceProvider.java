package com.iota.iri.service.tangle;

import com.iota.iri.model.*;
import org.rocksdb.RocksDBException;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/**
 * Created by paul on 3/2/17 for iri.
 */
public interface IPersistenceProvider {
    void init() throws Exception;
    void init(String path) throws Exception;
    void shutdown();
    boolean save(Object o) throws Exception;
    void delete(Object o) throws Exception;

    boolean update(Object model, String item) throws Exception;

    boolean setTransientHandle(Class<?> model, Object uuid) throws Exception;
    void dropTransientHandle(Object handle) throws Exception;
    boolean save(Object handle, Object model) throws Exception;
    boolean mayExist(Object handle, Object key) throws Exception;
    boolean exists(Class<?> model, Object key) throws Exception;
    Object get(Object handle, Class<?> model, Object key) throws Exception;
    void deleteTransientObject(Object uuid, Object key) throws Exception;
    void copyTransientList(Object sourceId, Object destId) throws Exception;

    Object latest(Class<?> model) throws  Exception;

    Object[] getKeys(Class<?> modelClass) throws Exception;

    boolean get(Transaction transaction) throws Exception;

    boolean get(Address address) throws Exception;

    boolean get(Tag tag) throws Exception;

    boolean get(Bundle bundle) throws Exception;

    boolean get(Approvee approvee) throws Exception;

    boolean mayExist(Scratchpad scratchpad) throws Exception;

    boolean mayExist(Transaction transaction) throws Exception;

    boolean mayExist(Tip tip) throws Exception;

    void updateType(Transaction transaction) throws Exception;

    boolean transientObjectExists(Object uuid, byte[] hash) throws Exception;
}
