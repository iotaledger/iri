package com.iota.iri.storage;

import com.iota.iri.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Delegates methods from {@link PersistenceProvider}
 */
public class Tangle {
    private static final Logger log = LoggerFactory.getLogger(Tangle.class);

    private final List<PersistenceProvider> persistenceProviders = new ArrayList<>();

    public void addPersistenceProvider(PersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }

    /**
     * @see PersistenceProvider#init()
     */
    public void init() throws Exception {
        for(PersistenceProvider provider: this.persistenceProviders) {
            provider.init();
        }
    }

    /**
     * @see PersistenceProvider#shutdown()
     */
    public void shutdown() throws Exception {
        log.info("Shutting down Tangle Persistence Providers... ");
        this.persistenceProviders.forEach(PersistenceProvider::shutdown);
        this.persistenceProviders.clear();
    }

    /**
     * @see PersistenceProvider#get(Class, Indexable)
     */
    public Persistable load(Class<?> model, Indexable index) throws Exception {
            Persistable out = null;
            for(PersistenceProvider provider: this.persistenceProviders) {
                if((out = provider.get(model, index)) != null) {
                    break;
                }
            }
            return out;
    }

    /**
     * @see PersistenceProvider#saveBatch(List)
     */
    public Boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        boolean exists = false;
        for(PersistenceProvider provider: persistenceProviders) {
            if(exists) {
                provider.saveBatch(models);
            } else {
                exists = provider.saveBatch(models);
            }
        }
        return exists;
    }

    /**
     * @see PersistenceProvider#save(Persistable, Indexable)
     */
    public Boolean save(Persistable model, Indexable index) throws Exception {
            boolean exists = false;
            for(PersistenceProvider provider: persistenceProviders) {
                if(exists) {
                    provider.save(model, index);
                } else {
                   exists = provider.save(model, index);
                }
            }
            return exists;
    }

    /**
     * @see PersistenceProvider#deleteBatch(Collection)
     */
    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.deleteBatch(models);
        }
    }

    /**
     * @see PersistenceProvider#delete(Class, Indexable)
     */
    public void delete(Class<?> model, Indexable index) throws Exception {
            for(PersistenceProvider provider: persistenceProviders) {
                provider.delete(model, index);
            }
    }

    /**
     * @see PersistenceProvider#latest(Class, Class)
     */
    public Pair<Indexable, Persistable> getLatest(Class<?> model, Class<?> index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if (latest == null) {
                    latest = provider.latest(model, index);
                }
            }
            return latest;
    }

    /**
     * @see PersistenceProvider#update(Persistable, Indexable, String)
     */
    public Boolean update(Persistable model, Indexable index, String item) throws Exception {
            boolean success = false;
            for(PersistenceProvider provider: this.persistenceProviders) {
                if(success) {
                    provider.update(model, index, item);
                } else {
                    success = provider.update(model, index, item);
                }
            }
            return success;
    }

    /**
     * @see PersistenceProvider#keysWithMissingReferences(Class, Class)
     */
    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> referencedClass) throws Exception {
            Set<Indexable> output = null;
            for(PersistenceProvider provider: this.persistenceProviders) {
                output = provider.keysWithMissingReferences(modelClass, referencedClass);
                if(output != null && output.size() > 0) {
                    break;
                }
            }
            return output;
    }

    /**
     * @see PersistenceProvider#keysStartingWith(Class, byte[])
     */
    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
            Set<Indexable> output = null;
            for(PersistenceProvider provider: this.persistenceProviders) {
                output = provider.keysStartingWith(modelClass, value);
                if(output.size() != 0) {
                    break;
                }
            }
            return output;
    }


    /**
     * @see PersistenceProvider#exists(Class, Indexable)
     */
    public Boolean exists(Class<?> modelClass, Indexable hash) throws Exception {
            for(PersistenceProvider provider: this.persistenceProviders) {
                if (provider.exists(modelClass, hash)) {
                    return true;
                }
            }
            return false;
    }

    /**
     * @see PersistenceProvider#mayExist(Class, Indexable)
     */
    public Boolean maybeHas(Class<?> model, Indexable index) throws Exception {
            for(PersistenceProvider provider: this.persistenceProviders) {
                if (provider.mayExist(model, index)) {
                    return true;
                }
            }
            return false;
    }

    /**
     * @see PersistenceProvider#count(Class)
     */
    public Long getCount(Class<?> modelClass) throws Exception {
            long value = 0;
            for(PersistenceProvider provider: this.persistenceProviders) {
                if((value = provider.count(modelClass)) != 0) {
                    break;
                }
            }
            return value;
    }

    /**
     * @see PersistenceProvider#seek(Class, byte[])
     */
    public Persistable find(Class<?> model, byte[] key) throws Exception {
            Persistable out = null;
            for (PersistenceProvider provider : this.persistenceProviders) {
                if ((out = provider.seek(model, key)) != null) {
                    break;
                }
            }
            return out;
    }

    /**
     * @see PersistenceProvider#next(Class, Indexable)
     */
    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if(latest == null) {
                    latest = provider.next(model, index);
                }
            }
            return latest;
    }

    /**
     * @see PersistenceProvider#previous(Class, Indexable)
     */
    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if(latest == null) {
                    latest = provider.previous(model, index);
                }
            }
            return latest;
    }

    /**
     * @see PersistenceProvider#first(Class, Class)
     */
    public Pair<Indexable, Persistable > getFirst(Class<?> model, Class<?> index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if(latest == null) {
                    latest = provider.first(model, index);
                }
            }
            return latest;
    }

    /**
     * @see PersistenceProvider#clear(Class)
     */
    public void clearColumn(Class<?> column) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.clear(column);
        }
    }

    /**
     * @see PersistenceProvider#clearMetadata(Class)
     */
    public void clearMetadata(Class<?> column) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.clearMetadata(column);
        }
    }
}
