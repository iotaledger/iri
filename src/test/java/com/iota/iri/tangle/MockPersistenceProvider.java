package com.iota.iri.tangle;

import com.iota.iri.model.Transaction;

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
    public void shutdown() {

    }

    @Override
    public boolean save(java.lang.Object o) throws Exception {
        return true;
    }

    @Override
    public boolean get(java.lang.Object c, java.lang.Object key) throws Exception {
        modelPrimaryKey.get(c.getClass()).set(c, key);
        return true;
    }

    @Override
    public void setColumns(Map<Class<?>, Field> modelPrimaryKey, Map<Class<?>, Map<String, ModelFieldInfo>> modelItems) {
        this.modelPrimaryKey = modelPrimaryKey;
        this.modelIndices = modelItems;
    }

    @Override
    public Object query(Class<?> model, String index, Object value, int keyLength) throws Exception {
        assert modelIndices.get(model.getClass()).get(index) != null;
        Object out = model.newInstance();
        modelPrimaryKey.get(model).set(out, "SomeBadVal".getBytes());
        return out;
    }


    public boolean query(java.lang.Object model, String index, java.lang.Object value) throws Exception {
        assert modelIndices.get(model.getClass()).get(index) != null;
        modelPrimaryKey.get(model.getClass()).set(model, "SomeBadVal".getBytes());
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

