package com.iota.iri.tangle;

import org.omg.CORBA.Object;

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
    public boolean save(java.lang.Object o) {
        return true;
    }

    @Override
    public boolean get(java.lang.Object c, java.lang.Object key) throws Exception {
        c.getClass().getDeclaredField(modelPrimaryKey.get(c.getClass())).set(c, key);
        return true;
    }

    @Override
    public void setColumns(Map<Class<?>, String> modelPrimaryKey, Map<Class<?>, Set<String>> modelIndices, Map<Class<?>, Set<String>> modelStoredItems) {
        this.modelPrimaryKey = modelPrimaryKey;
        this.modelIndices = modelIndices;
        this.modelStoredItems = modelStoredItems;
    }

    @Override
    public boolean query(java.lang.Object model, String index, java.lang.Object value) throws Exception {
        assert modelIndices.get(model.getClass()).contains(index);
        model.getClass().getDeclaredField(modelPrimaryKey.get(model.getClass())).set(model, "Some bad value".getBytes());
        return true;
    }

}

