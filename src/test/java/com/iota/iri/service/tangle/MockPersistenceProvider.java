package com.iota.iri.service.tangle;


import com.iota.iri.model.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * Created by paul on 3/4/17 for iri.
 */
public class MockPersistenceProvider implements IPersistenceProvider {

    private Map<Class<?>, Field> modelPrimaryKey;
    private Map<Class<?>, Map<String, ModelFieldInfo>> modelIndices;
    private Map<Class<?>, Set<String>> modelStoredItems;

    @Override
    public void init() throws Exception {

    }

    @Override
    public void init(String path) throws Exception {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public boolean saveTransaction(Transaction transaction) throws Exception {
        return false;
    }

    @Override
    public boolean save(java.lang.Object o) throws Exception {
        return true;
    }

    @Override
    public void delete(Object o) throws Exception {

    }


    @Override
    public boolean update(Object model, String item) throws Exception {
        return false;
    }

    @Override
    public void dropTransientHandle(Object handle) {

    }

    @Override
    public boolean save(Object handle, Object model) throws Exception {
        return false;
    }

    @Override
    public boolean mayExist(Object handle, Object key) {
        return false;
    }


    @Override
    public boolean exists(Class<?> model, Object key) throws Exception {
        return false;
    }

    @Override
    public Object get(Object handle, Class<?> model, Object key) throws Exception {
        return null;
    }

    @Override
    public void deleteTransientObject(Object uuid, Object key) throws Exception {

    }

    @Override
    public void copyTransientList(Object sourceId, Object destId) throws Exception {

    }

    @Override
    public Object latest(Class<?> model) throws Exception {
        return null;
    }

    @Override
    public Object[] getKeys(Class<?> modelClass) throws Exception {
        return new Object[0];
    }

    @Override
    public boolean get(Transaction transaction) throws Exception {
        return false;
    }

    @Override
    public boolean get(Address address) throws Exception {
        return false;
    }

    @Override
    public boolean get(Tag tag) throws Exception {
        return false;
    }

    @Override
    public boolean get(Bundle bundle) throws Exception {
        return false;
    }

    @Override
    public boolean get(Approvee approvee) throws Exception {
        return false;
    }

    @Override
    public boolean mayExist(Scratchpad scratchpad) throws Exception {
        return false;
    }

    @Override
    public boolean mayExist(Transaction transaction) throws Exception {
        return false;
    }

    @Override
    public boolean mayExist(Tip tip) throws Exception {
        return false;
    }

    @Override
    public void updateType(Transaction transaction) throws Exception {

    }

    @Override
    public boolean transientObjectExists(Object uuid, byte[] hash) throws Exception {
        return false;
    }

    @Override
    public void flushAnalyzedFlags() throws Exception {

    }

    @Override
    public void flushScratchpad() throws Exception {

    }

    @Override
    public long getNumberOfTransactions() throws Exception {
        return 0;
    }

    @Override
    public long getNumberOfRequestedTransactions() throws Exception {
        return 0;
    }

    @Override
    public boolean transactionExists(byte[] hash) throws Exception {
        return false;
    }

    @Override
    public boolean setTransientFlagHandle(Object uuid) throws Exception {
        return false;
    }

    @Override
    public void flushTransientFlags(Object id) throws Exception {

    }

}

