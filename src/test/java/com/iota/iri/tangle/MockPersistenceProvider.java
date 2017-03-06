package com.iota.iri.tangle;

import com.iota.iri.model.Transaction;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * Created by paul on 3/4/17 for iri.
 */
public class MockPersistenceProvider implements IPersistenceProvider {

    private Map<Class<?>, String> modelPrimaryKey;
    private Map<Class<?>, Set<String>> modelIndices;
    private Map<Class<?>, Set<String>> modelStoredItems;

    @Override
    public void init() throws Exception {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public boolean save(java.lang.Object o) throws Exception {
        return true;
    }

    @Override
    public boolean get(java.lang.Object c, java.lang.Object key) throws Exception {
        c.getClass().getDeclaredField(modelPrimaryKey.get(c.getClass())).set(c, key);
        return true;
    }

    @Override
    public void setColumns(Map<Class<?>, Field> modelPrimaryKey, Map<Class<?>, Set<Field>> modelIndices, Map<Class<?>, Set<Field>> modelStoredItems) {
        /*
        this.modelPrimaryKey = modelPrimaryKey;
        this.modelIndices = modelIndices;
        this.modelStoredItems = modelStoredItems;
        */
    }

    @Override
    public boolean query(java.lang.Object model, String index, java.lang.Object value) throws Exception {
        assert modelIndices.get(model.getClass()).contains(index);
        model.getClass().getDeclaredField(modelPrimaryKey.get(model.getClass())).set(model, "Some bad value".getBytes());
        return true;
    }

    @Override
    public boolean update(Object model, String item, Object value) {
        return true;
    }

    @Override
    public Object[] queryMany(Class<?> modelClass, String index, Object key, int hashLength) throws Exception {
        return new Object[0];
    }

}

