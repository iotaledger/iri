package com.iota.iri.storage;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.model.LocalSnapshot;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Pair;

import java.util.HashMap;
import java.util.List;

/**
 * Abstraction for localsnapshots-db persistence provider.
 */
public class LocalSnapshotsPersistenceProvider {
    private PersistenceProvider provider;
    private IotaConfig config;

    public LocalSnapshotsPersistenceProvider(IotaConfig config){
        this.config = config;
    }

    /**
     * Initialize the provider
     * @throws Exception
     */
    public void init() throws Exception{
        provider = new RocksDBPersistenceProvider(
                config.getLocalSnapshotsDbPath(),
                config.getLocalSnapshotsDbLogPath(),
                1000,
                new HashMap<String, Class<? extends Persistable>>(1) {{
                    put("spent-addresses", SpentAddress.class);
                    put("localsnapshots", LocalSnapshot.class);
                }}, null);
        provider.init();
    }

    /**
     * Shut down the local snapshots persistence provider
     */
    public void shutdown(){
        this.provider.shutdown();
    }

    /**
     * @see PersistenceProvider#saveBatch(List)
     */
    public Boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        return provider.saveBatch(models);
    }

    /**
     * @see PersistenceProvider#save(Persistable, Indexable)
     */
    public Boolean save(Persistable model, Indexable index) throws Exception {
        return provider.save(model, index);
    }

    /**
     * @see PersistenceProvider#exists(Class, Indexable)
     */
    public Boolean exists(Class<?> modelClass, Indexable hash) throws Exception {
        return provider.exists(modelClass, hash);
    }

    /**
     * @see PersistenceProvider#first(Class, Class)
     */
    public Pair<Indexable, Persistable > getFirst(Class<?> model, Class<?> index) throws Exception {
        return provider.first(model, index);
    }

    /**
     * Get the local snapshots persistence provider
     * @return      The active local snapshots persistence provider
     */
    public PersistenceProvider getProvider(){
        return this.provider;
    }
}
