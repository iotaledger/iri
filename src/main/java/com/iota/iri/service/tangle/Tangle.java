package com.iota.iri.service.tangle;

import com.iota.iri.model.*;
import com.iota.iri.service.tangle.annotations.*;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/3/17 for iri.
 */
public class Tangle {
    private static final char COLUMN_DELIMETER = '.';
    private static final Logger log = LoggerFactory.getLogger(Tangle.class);

    private static Tangle instance = new Tangle();
    List<IPersistenceProvider> persistenceProviders = new ArrayList<>();
    private ExecutorService executor;
    private final Map<Class<?>, Field> modelPrimaryKeys = new HashMap<>();
    private final Map<Class<?>, Map<String, ModelFieldInfo>> modelFieldInfo = new HashMap<>();
    private final List<UUID> transientDBList = new ArrayList<>();
    private boolean shutdown;


    public void addPersistenceProvider(IPersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }

    public void init(String path) throws Exception {
        executor = Executors.newCachedThreadPool();
        for(IPersistenceProvider provider: this.persistenceProviders) {
            //provider.setColumns(modelPrimaryKeys, modelFieldInfo);
            provider.init(path);
        }
    }
    public void init() throws Exception {
        executor = Executors.newCachedThreadPool();
        for(IPersistenceProvider provider: this.persistenceProviders) {
            //provider.setColumns(modelPrimaryKeys, modelFieldInfo);
            provider.init();
        }
    }

    public boolean availalbe() {
        return !shutdown;
    }

    public void shutdown() throws Exception {
        log.info("Shutting down Tangle Persistence Providers... ");
        shutdown = true;
        executor.awaitTermination(6, TimeUnit.SECONDS);
        for(UUID uuid: transientDBList) {
            dropList(uuid);
        }
        this.persistenceProviders.forEach(IPersistenceProvider::shutdown);
        this.persistenceProviders.clear();
    }

    public Object createTransientList(Class<?> model) throws Exception {
        UUID uuid = UUID.randomUUID();
        for(IPersistenceProvider provider: this.persistenceProviders) {
            provider.setTransientHandle(model,(Object) uuid);
        }
        return uuid;
    }
    public void dropList(Object uuid) throws Exception {
        for(IPersistenceProvider provider : persistenceProviders) {
            provider.dropTransientHandle(uuid);
        }
        this.transientDBList.remove(uuid);
    }

    public List<IPersistenceProvider> getPersistenceProviders() {
        return this.persistenceProviders;
    }

    private String referenceFieldName(Class<?> model, Field field) {
        return model.getName() + COLUMN_DELIMETER + field.getName();
    }

    /*
    public Future<Object> query(Object model, String index, Object value) {
        return executor.submit(() -> {
            boolean success = false;
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.query(model, index, value)) {
                    success = true;
                    break;
                }
            }
            return success;
        });
    }
    */

    public Future<Boolean> load(Transaction transaction) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.get(transaction)) {
                    return true;
                }
            }
            return false;
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


    public Future<Boolean> maybeHas(Object handle, Object key) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(provider.mayExist(handle, key)) return true;
            }
            return false;
        });
    }

    public Future<Void> delete(Object handle, Object model) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                provider.deleteTransientObject(handle, model);
            }
            return null;
        });
    }
    public Future<Boolean> save(Object handle, Object model) {
        return executor.submit(() -> {
            for(IPersistenceProvider provider: this.persistenceProviders) {
                if(!provider.save(handle, model)) {
                    return false;
                }
            }
            return true;
        });
    }
    public Future<Object> load(Object handle, Class<?> model, byte[] key) {
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

    public Future<Void> copyTransientList(Object sourceHandle, Object destHandle) {
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
}
