package com.iota.iri.service.tangle;

import com.iota.iri.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by paul on 3/3/17 for iri.
 */
public class Tangle {
    private static final char COLUMN_DELIMETER = '.';
    private static final Logger log = LoggerFactory.getLogger(Tangle.class);

    private static Tangle instance = new Tangle();
    List<IPersistenceProvider> persistenceProviders = new ArrayList<>();
    private ExecutorService executor;
    private final List<Integer> transientHandles = new ArrayList<>();
    private final List<Integer> availableTansientTables = new ArrayList<>();
    private final List<Integer> transientTablesInUse = new ArrayList<>();
    private volatile int nextTableId = 1;

    public void addPersistenceProvider(IPersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }

    public void init(String path) throws Exception {
        executor = Executors.newCachedThreadPool();
        for(IPersistenceProvider provider: this.persistenceProviders) {
            provider.init(path);
        }
    }
    public void init() throws Exception {
        executor = Executors.newCachedThreadPool();
        for(IPersistenceProvider provider: this.persistenceProviders) {
            provider.init();
        }
    }


    public void shutdown() throws Exception {
        log.info("Shutting down Tangle Persistence Providers... ");
        for(int id: transientHandles) {
            releaseTransientTable(id);
        }
        this.persistenceProviders.forEach(IPersistenceProvider::shutdown);
        this.persistenceProviders.clear();
    }

    public int createTransientFlagList() throws Exception {
        int id;
        synchronized (this) {
            if(availableTansientTables.size() > 0) {
                id = availableTansientTables.remove(0);
            } else {
                id = nextTableId++;
                for(IPersistenceProvider provider: this.persistenceProviders) {
                    provider.setTransientFlagHandle(id);
                }
                transientHandles.add(id);
            }
        }
        transientTablesInUse.add(id);
        return id;
    }

    public void releaseTransientTable(int id) throws Exception {
        for(IPersistenceProvider provider : persistenceProviders) {
            provider.flushTagRange( id);
        }
        synchronized (this) {
            log.info("Released transient table with id: " + id);
            availableTansientTables.add(id);
            transientTablesInUse.remove((Object)id);
        }
    }

    public List<IPersistenceProvider> getPersistenceProviders() {
        return this.persistenceProviders;
    }

    private String referenceFieldName(Class<?> model, Field field) {
        return model.getName() + COLUMN_DELIMETER + field.getName();
    }

    public boolean loadNow(Transaction transaction) throws Exception {
        for(IPersistenceProvider provider: this.persistenceProviders) {
            if(provider.get(transaction)) {
                return true;
            }
        }
        return false;
    }
    public Future<Boolean> load(Transaction transaction) {
        return executor.submit(() -> {
            return loadNow(transaction);
        });
    }

    public Future<Boolean> load(Address address) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.get(address)) {
                    return true;
                }
            }
            return false;
        });
    }

    public Future<Boolean> load(Tag tag) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.get(tag)) {
                    return true;
                }
            }
            return false;
        });
    }

    public Future<Boolean> load(Bundle bundle) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.get(bundle)) {
                    return true;
                }
            }
            return false;
        });
    }

    public Future<Boolean> load(Approvee approvee) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.get(approvee)) {
                    return true;
                }
            }
            return false;
        });
    }

    public Future<Boolean> save(Transaction transaction) {
        return executor.submit(() -> {
            boolean saved = false;
            for(IPersistenceProvider provider: persistenceProviders) {
                if(saved = provider.saveTransaction(transaction)) {
                    break;
                }
            }
            return saved;
        });
    }

    public Future<Boolean> save(Object model) {
        return executor.submit(() -> {
            boolean exists = false;
            for(IPersistenceProvider provider: persistenceProviders) {
                if(exists = provider.save(model)) {
                    break;
                }
            }
            return exists;
        });
    }

    public Future<Void> delete(Object model) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: persistenceProviders) {
                provider.delete(model);
            }
            return null;
        });
    }

    public Future<Object> getLatest(Class<?> model) {
        return executor.submit(() -> {
            Object latest = null;
            for(IPersistenceProvider provider: persistenceProviders) {
                latest = provider.latest(model);
            }
            return latest;
        });
    }

    public Future<Boolean> update(Object model, String item, Object value) {
        return executor.submit(() -> {
            boolean success = true;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(!provider.update(model, item)) {
                    success = false;
                    break;
                }
            }
            return success;
        });
    }

    public static Tangle instance() {
        return instance;
    }


    public Future<Boolean> maybeHas(int handle, Object key) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.mayExist(handle, key)) return true;
            }
            return false;
        });
    }

    public Future<Boolean> save(int handle, Object model) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(!provider.save(handle, model)) {
                    return false;
                }
            }
            return true;
        });
    }
    public Future<Object> load(int handle, Class<?> model, byte[] key) {
        return executor.submit(() -> {
            Object loadableObject = null;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                loadableObject = provider.get(handle, model, key);
                if(loadableObject != null) {
                    break;
                }
            }
            return loadableObject;
        });
    }

    public Future<Void> copyTransientList(int sourceHandle, int destHandle) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                provider.copyTransientList(sourceHandle, destHandle);
            }
            return null;
        });
    }

    public Future<Object[]> loadAll(Class<?> modelClass) {
        return executor.submit(() -> {
            Object[] output = new Object[0];
            for(IPersistenceProvider provider: this.persistenceProviders) {
                output = provider.getKeys(modelClass);
                if(output != null && output.length > 0) {
                    break;
                }
            }
            return output;
        });
    }

    public Future<Boolean> transientExists(int uuid, byte[] hash) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.transientObjectExists(uuid, hash)) return true;
            }
            return false;
        });
    }

    public boolean transactionExists(BigInteger hash) throws Exception {
        for(IPersistenceProvider provider: this.persistenceProviders) {
            if(provider.transactionExists(hash)) return true;
        }
        return false;
    }
    public Future<Boolean> exists(Class<?> modelClass, byte[] hash) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.exists(modelClass, hash)) return true;
            }
            return false;
        });
    }

    public Future<Boolean> maybeHas(Transaction transaction) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.mayExist(transaction)) return true;
            }
            return false;
        });
    }

    public Future<Boolean> maybeHas(Scratchpad scratchpad) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.mayExist(scratchpad)) return true;
            }
            return false;
        });
    }

    public Future<Boolean> maybeHas(Tip tip) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.mayExist(tip)) return true;
            }
            return false;
        });
    }

    public Future<Void> updateType(Transaction transaction) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                provider.updateType(transaction);
            }
            return null;
        });
    }

    public Future<Void> flushTransientFlags(int id) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                provider.flushTagRange(id);
            }
            return null;
        });
    }

    public Future<Void> flushAnalyzedFlags() {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                provider.flushAnalyzedFlags();
            }
            return null;
        });
    }

    public Future<Long> getNumberOfStoredTransactions() {
        return executor.submit(() -> {
            long value = 0;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if((value = provider.getNumberOfTransactions()) != 0) {
                    break;
                }
            }
            return value;
        });
    }

    public Future<Long> getNumberOfRequestedTransactions() {
        return executor.submit(() -> {
            long value = 0;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if((value = provider.getNumberOfRequestedTransactions()) != 0) {
                    break;
                }
            }
            return value;
        });
    }

    public Future<Void> flushScratchpad() {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                provider.flushScratchpad();
            }
            return null;
        });
    }
}
