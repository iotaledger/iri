package com.iota.iri.storage;

import com.iota.iri.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Created by paul on 3/3/17 for iri.
 */
public class Tangle {
    private static final Logger log = LoggerFactory.getLogger(Tangle.class);

    private static final Tangle instance = new Tangle();
    private final List<PersistenceProvider> persistenceProviders = new ArrayList<>();
    private ExecutorService executor;

    public void addPersistenceProvider(PersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }

    public void init() throws Exception {
        executor = Executors.newCachedThreadPool();
        for(PersistenceProvider provider: this.persistenceProviders) {
            provider.init();
        }
    }

    public int getActiveThreads() {
        return ((ThreadPoolExecutor) executor).getActiveCount();
    }


    public void shutdown() throws Exception {
        log.info("Shutting down Tangle Persistence Providers... ");
        executor.shutdown();
        executor.awaitTermination(6, TimeUnit.SECONDS);
        this.persistenceProviders.forEach(PersistenceProvider::shutdown);
        this.persistenceProviders.clear();
    }

    private boolean loadNow(Object object) throws Exception {
        for(PersistenceProvider provider: this.persistenceProviders) {
            //while(!provider.isAvailable()) {}
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
            for(PersistenceProvider provider: persistenceProviders) {
                if(exists) {
                    provider.save(model);
                } else {
                   exists = provider.save(model);
                }
            }
            return exists;
        });
    }

    public Future<Void> delete(Object model) {
        return executor.submit(() -> {
            for(PersistenceProvider provider: persistenceProviders) {
                //while(!provider.isAvailable()) {}
                provider.delete(model);
            }
            return null;
        });
    }

    public Future<Object> getLatest(Class<?> model) {
        return executor.submit(() -> {
            Object latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                //while(!provider.isAvailable()) {}
                if (latest == null) {
                    latest = provider.latest(model);
                }
            }
            return latest;
        });
    }

    public Future<Boolean> update(Object model, String item) {
        return executor.submit(() -> {
            boolean success = false;
            for(PersistenceProvider provider: this.persistenceProviders) {
                if(success) {
                    provider.update(model, item);
                } else {
                    success = provider.update(model, item);
                }
            }
            return success;
        });
    }

    public static Tangle instance() {
        return instance;
    }

    public Future<Object[]> keysWithMissingReferences(Class<?> modelClass) {
        return executor.submit(() -> {
            Object[] output = new Object[0];
            for(PersistenceProvider provider: this.persistenceProviders) {
                //while(!provider.isAvailable()) {}
                output = provider.keysWithMissingReferences(modelClass);
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
            for(PersistenceProvider provider: this.persistenceProviders) {
                //while(!provider.isAvailable()) {}
                output = provider.keysStartingWith(modelClass, value);
                if(output.length != 0) {
                    break;
                }
            }
            return output;
        });
    }

    public Future<Boolean> exists(Class<?> modelClass, Hash hash) {
        return executor.submit(() -> {
            for(PersistenceProvider provider: this.persistenceProviders) {
                //while(!provider.isAvailable()) {}
                if(provider.exists(modelClass, hash)) return true;
            }
            return false;
        });
    }

    public Future<Boolean> maybeHas(Object object) {
        return executor.submit(() -> {
            for(PersistenceProvider provider: this.persistenceProviders) {
                //while(!provider.isAvailable()) {}
                if(provider.mayExist(object)) return true;
            }
            return false;
        });
    }

    public Future<Long> getCount(Class<?> modelClass) {
        return executor.submit(() -> {
            long value = 0;
            for(PersistenceProvider provider: this.persistenceProviders) {
                //while(!provider.isAvailable()) {}
                if((value = provider.count(modelClass)) != 0) {
                    break;
                }
            }
            return value;
        });
    }

    public Future<Object> find(Class<?> model, byte[] key) {
        return executor.submit(() -> {
            Object out = null;
            for (PersistenceProvider provider : this.persistenceProviders) {
                //while(!provider.isAvailable()) {}
                if ((out = provider.seek(model, key)) != null) {
                    break;
                }
            }
            return out;
        });
    }

    public Future<Object> next(Class<?> model, int index) {
        return executor.submit(() -> {
            Object latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                //while(!provider.isAvailable()) {}
                if(latest == null) {
                    latest = provider.next(model, index);
                }
            }
            return latest;

        });
    }
    public Future<Object> previous(Class<?> model, int index) {
        return executor.submit(() -> {
            Object latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                //while(!provider.isAvailable()) {}
                if(latest == null) {
                    latest = provider.previous(model, index);
                }
            }
            return latest;

        });
    }

    public Future<Object> getFirst(Class<?> model) {
        return executor.submit(() -> {
            Object latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                //while(!provider.isAvailable()) {}
                if(latest == null) {
                    latest = provider.first(model);
                }
            }
            return latest;
        });
    }
}
