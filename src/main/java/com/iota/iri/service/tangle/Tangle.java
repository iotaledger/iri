package com.iota.iri.service.tangle;

import com.iota.iri.model.*;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Created by paul on 3/3/17 for iri.
 */
public class Tangle {
    private static final Logger log = LoggerFactory.getLogger(Tangle.class);

    private static Tangle instance = new Tangle();
    List<IPersistenceProvider> persistenceProviders = new ArrayList<>();
    private ExecutorService executor;
    private boolean available = true;

    public void addPersistenceProvider(IPersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }

    public void init() throws Exception {
        executor = Executors.newCachedThreadPool();
        for(IPersistenceProvider provider: this.persistenceProviders) {
            provider.init();
        }
        //new TransactionViewModel(TransactionViewModel.NULL_TRANSACTION_BYTES, new int[TransactionViewModel.TRINARY_SIZE], null).store();
    }


    public void shutdown() throws Exception {
        log.info("Shutting down Tangle Persistence Providers... ");
        this.available = false;
        executor.shutdown();
        executor.awaitTermination(6, TimeUnit.SECONDS);
        this.persistenceProviders.forEach(IPersistenceProvider::shutdown);
        this.persistenceProviders.clear();
    }

    private boolean loadNow(Object object) throws Exception {
        for(IPersistenceProvider provider: this.persistenceProviders) {
            if(provider.get(object)) {
                return true;
            }
        }
        return false;
    }

    public Future<Boolean> load(Object object) {
        return executor.submit(() -> loadNow(object));
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

    public Future<Boolean> update(Object model, String item) {
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

    public Future<Object[]> scanForTips(Class<?> modelClass) {
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

    public Future<Hash[]> keysStartingWith(Class<?> modelClass, byte[] value) {
        return executor.submit(() -> {
            Hash[] output = new Hash[0];
            for(IPersistenceProvider provider: this.persistenceProviders) {
                output = provider.keysStartingWith(modelClass, value);
                if(output.length != 0);
            }
            return output;
        });
    }

    public Future<Boolean> exists(Class<?> modelClass, Hash hash) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.exists(modelClass, hash)) return true;
            }
            return false;
        });
    }

    public Future<Boolean> maybeHas(Object object) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.mayExist(object)) return true;
            }
            return false;
        });
    }

    public Future<Long> getCount(Class<?> modelClass) {
        return executor.submit(() -> {
            long value = 0;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if((value = provider.count(modelClass)) != 0) {
                    break;
                }
            }
            return value;
        });
    }
}
