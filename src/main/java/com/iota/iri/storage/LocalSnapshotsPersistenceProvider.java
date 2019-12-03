package com.iota.iri.storage;

import com.iota.iri.utils.Pair;

import java.util.List;

/**
 * Abstraction for localsnapshots-db persistence provider.
 */
public class LocalSnapshotsPersistenceProvider {
    private PersistenceProvider provider;

    /**
     * Initialize the provider
     * @throws Exception
     */
    public void init() throws Exception{
        provider.init();
    }

    /**
     * Inject the localsnapshots-db persistence provider into the class
     *
     * @param persistenceProvider   The persistence provider instance for the localsnapshots-db
     */
    public void injectProvider(PersistenceProvider persistenceProvider){
        this.provider = persistenceProvider;
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
        if(provider.getClass().desiredAssertionStatus()) {
            return provider.save(model, index);
        }
        return false;
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
