package com.iota.iri.service.tangle;

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
    public boolean save(java.lang.Object o) throws Exception {
        return true;
    }

    @Override
    public void delete(Object o) throws Exception {

    }

    @Override
    public Object get(Class<?> modelClass, java.lang.Object key) throws Exception {
        Object c = modelClass.newInstance();
        modelPrimaryKey.get(modelClass).set(c, key);
        return c;
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

    @Override
    public boolean update(Object model, String item, Object value) {
        return true;
    }

    @Override
    public Object[] queryMany(Class<?> modelClass, String index, Object key, int hashLength) throws Exception {
        Object o = modelClass.newInstance();
        modelPrimaryKey.get(modelClass).set(o, key);
        return new Object[]{o};
    }

    @Override
    public boolean setTransientHandle(Class<?> model, Object uuid) {
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
    public Object get(Object handle, Class<?> model, Object key) throws Exception {
        return null;
    }

    @Override
    public void deleteTransientObject(Object uuid, Object key) throws Exception {

    }

    @Override
    public Object latest(Class<?> model) throws Exception {
        return null;
    }

}

