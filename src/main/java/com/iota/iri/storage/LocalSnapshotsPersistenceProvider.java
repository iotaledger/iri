package com.iota.iri.storage;

import com.iota.iri.model.LocalSnapshot;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.utils.Pair;

import java.util.*;

/**
 * Abstraction for localsnapshots-db persistence provider.
 */
public class LocalSnapshotsPersistenceProvider implements PersistenceProvider {
    private PersistenceProvider provider;

    public static final Map<String, Class<? extends Persistable>> COLUMN_FAMILIES =
            new LinkedHashMap<String, Class<? extends Persistable>>() {{
                put("spent-addresses", SpentAddress.class);
                put("localsnapshots", LocalSnapshot.class);
            }};

    /**
     * Constructor for {@link LocalSnapshotsPersistenceProvider}. Stores the {@link PersistenceProvider} to act as an
     * abstraction of the localsnapshots-db. Restricts access to some db functionality.
     *
     * @param provider  The {@link PersistenceProvider} that will be stored
     */
    public LocalSnapshotsPersistenceProvider(PersistenceProvider provider){
        this.provider = provider;
    }

    /**
     * {@inheritDoc}
     */
    public void init() throws Exception{
        provider.init();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown(){
        this.provider.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAvailable(){
        return this.provider.isAvailable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        return provider.saveBatch(models);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean save(Persistable model, Indexable index) throws Exception {
        return provider.save(model, index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(Class<?> modelClass, Indexable hash) throws Exception {
        return provider.exists(modelClass, hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Class<?> modelClass, Indexable hash) throws Exception {
        provider.delete(modelClass, hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean update(Persistable model, Indexable index, String item) throws Exception{
        return provider.update(model, index, item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Pair<Indexable, Persistable > first(Class<?> model, Class<?> index) throws Exception {
        return provider.first(model, index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception {
        return  provider.latest(model, indexModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long count(Class<?> model) throws Exception {
        return provider.count(model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Persistable get(Class<?> model, Indexable index) throws Exception {
        return provider.get(model, index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception {
        provider.deleteBatch(models);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<byte[]> loadAllKeysFromTable(Class<? extends Persistable> model) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getPersistenceSize() {
        throw new UnsupportedOperationException(
                "Local Snapshots Persistance Provider can't know the size of SST files");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
        return provider.next(model, index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
        return provider.previous(model, index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear(Class<?> column) throws Exception {
        provider.clear(column);
    }


    ////////////////////////////////////////////////////////////////////////////////////
    ////////// Unsupported methods /////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Persistable seek(Class<?> model, byte[] key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> otherClass){
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean mayExist(Class<?> model, Indexable index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
        throw new UnsupportedOperationException();
    }


    @Override
    public void clearMetadata(Class<?> column) {
        throw new UnsupportedOperationException();
    }
}
