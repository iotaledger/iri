package com.iota.iri.service.tangle;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
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
    private final List<Integer> availableTansientTables = new ArrayList<>();
    private final List<Integer> transientTablesInUse = new ArrayList<>();
    private volatile int nextTableId = 0;
    private boolean available = true;
    private Thread flusher;
    private boolean flushing = true;

    public void addPersistenceProvider(IPersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }

    public void init() throws Exception {
        executor = Executors.newCachedThreadPool();
        for(IPersistenceProvider provider: this.persistenceProviders) {
            provider.init();
        }

        this.flusher = new Thread(() -> flushTransientTables(), "Table Flush Thread");
        flusher.start();
    }

    public void shutdown() throws Exception {
        log.info("Shutting down Tangle Persistence Providers... ");
        this.available = false;
        executor.shutdown();
        executor.awaitTermination(6, TimeUnit.SECONDS);
        flusher.join();
        for(int id: transientTablesInUse) {
            for (IPersistenceProvider provider : persistenceProviders) {
                provider.flushTagRange(id);
            }
        }
        this.persistenceProviders.forEach(IPersistenceProvider::shutdown);
        this.persistenceProviders.clear();
    }

    public int createTransientFlagList() throws Exception {
        int id;
        boolean create = false;
        while(flushing) {}
        synchronized (this) {
            if(availableTansientTables.size() > 0) {
                id = availableTansientTables.remove(0);
            } else {
                id = nextTableId++;
                create = true;
            }
            transientTablesInUse.add(id);
        }
        if(create && available) {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                provider.setTransientFlagHandle(id);
            }
            return id;
        }
        return id;
    }

    public void releaseTransientTable(int id) throws Exception {
        if(available) {
            flushTransientFlags(id).get();
        }
        synchronized (this) {
            log.info("Released transient table with id: " + id);
            availableTansientTables.add(id);
            transientTablesInUse.remove((Object)id);
        }
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


    public Future<Boolean> maybeHas(int handle, Hash key) {
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
                if(provider.save(handle, model)) {
                    return true;
                }
            }
            return false;
        });
    }
    public Future<Object> load(int handle, Class<?> model, Hash key) {
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

    public Future<Void> flushTransientFlags(int id) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                provider.flushTagRange(id);
            }
            return null;
        });
    }

    public Future<Void> flush(Class<?> modelClass) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                provider.flush(modelClass);
            }
            return null;
        });
    }

    public Future<Long> getCount(int transientId) {
        return executor.submit(() -> {
            long value = 0;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if((value = provider.count(transientId)) != 0) {
                    break;
                }
            }
            return value;
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

    private void flushTransientTables() {
        int sleepDuration, count, maxTables, i, end;
        sleepDuration = Configuration.integer(Configuration.DefaultConfSettings.FLUSH_PERIOD);
        maxTables = Configuration.integer(Configuration.DefaultConfSettings.MAX_TRANSIENT_TABLES);
        end = maxTables;
        count = sleepDuration;
        Set<Future> futures = new HashSet<>();
        while(available) {
            try {
                if(count++ > sleepDuration) {
                    count = 0;
                    this.flushing = true;
                    while(transientTablesInUse.size() != 0) {}
                    for(i = 0; i < end; i++) {
                        futures.add(flushTransientFlags(i));
                    }
                    for(Future f: futures) {
                        f.get();
                    }
                    futures.clear();
                    end = nextTableId;
                    this.flushing = false;
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("Flush Error: " + e.getMessage());
            }
        }
    }
}
