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
    boolean saveTransaction(Transaction transaction) throws Exception;
    boolean save(Object o) throws Exception;
    void delete(Object o) throws Exception;

    boolean update(Object model, String item) throws Exception;

    boolean save(int handle, Object model) throws Exception;
    boolean mayExist(int handle, Hash key) throws Exception;
    boolean exists(Class<?> model, Hash key) throws Exception;
    Object get(int handle, Class<?> model, Hash key) throws Exception;
    void deleteTransientObject(int uuid, Hash key) throws Exception;
    void copyTransientList(int sourceId, int destId) throws Exception;

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

    boolean transientObjectExists(int uuid, Hash hash) throws Exception;

    void flushAnalyzedFlags() throws Exception;

    void flushScratchpad() throws Exception;

    long getNumberOfTransactions() throws Exception;

    long getNumberOfRequestedTransactions() throws Exception;

    boolean transactionExists(Hash hash) throws Exception;

    boolean setTransientFlagHandle(int uuid) throws Exception;

    void flushTagRange(int id) throws Exception;
}
