package com.iota.iri.storage;

import com.iota.iri.model.Hash;
import com.iota.iri.model.StateDiff;
import com.iota.iri.model.persistables.Address;
import com.iota.iri.model.persistables.Approvee;
import com.iota.iri.model.persistables.Bundle;
import com.iota.iri.model.persistables.Milestone;
import com.iota.iri.model.persistables.ObsoleteTag;
import com.iota.iri.model.persistables.Tag;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.utils.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.iota.iri.zmq.MessageQueueProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.naming.OperationNotSupportedException;

/**
 * Delegates methods from {@link PersistenceProvider}
 */
public class Tangle {
    private static final Logger log = LoggerFactory.getLogger(Tangle.class);

    public static final Map<String, Class<? extends Persistable>> COLUMN_FAMILIES =
            new LinkedHashMap<String, Class<? extends Persistable>>() {{
                put("transaction", Transaction.class);
                put("milestone", Milestone.class);
                put("stateDiff", StateDiff.class);
                put("address", Address.class);
                put("approvee", Approvee.class);
                put("bundle", Bundle.class);
                put("obsoleteTag", ObsoleteTag.class);
                put("tag", Tag.class);
            }};

    public static final Map.Entry<String, Class<? extends Persistable>> METADATA_COLUMN_FAMILY =
            new AbstractMap.SimpleImmutableEntry<>("transaction-metadata", Transaction.class);

    private final List<PersistenceProvider> persistenceProviders = new ArrayList<>();
    private final List<MessageQueueProvider> messageQueueProviders = new ArrayList<>();

    public void addPersistenceProvider(PersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }

    /**
     * 
     * @see PersistenceProvider#init()
     */
    public void init() throws Exception {
        for(PersistenceProvider provider: this.persistenceProviders) {
            provider.init();
        }
    }

    /**
     * Adds {@link com.iota.iri.zmq.MessageQueueProvider} that should be notified.
     * 
     * @param provider that should be notified.
     */
    public void addMessageQueueProvider(MessageQueueProvider provider) {
        this.messageQueueProviders.add(provider);
    }

    /**
     * @see PersistenceProvider#shutdown()
     */
    public void shutdown() throws Exception {
        log.info("Shutting down Tangle Persistence Providers... ");
        this.persistenceProviders.forEach(PersistenceProvider::shutdown);
        this.persistenceProviders.clear();
        log.info("Shutting down Tangle MessageQueue Providers... ");
        this.messageQueueProviders.forEach(MessageQueueProvider::shutdown);
        this.messageQueueProviders.clear();
    }

    /**
     * @see PersistenceProvider#get(Class, Indexable)
     */
    public Persistable load(Class<?> model, Indexable index) throws Exception {
        LinkedList<Persistable> outlist = new LinkedList<>();
        for (PersistenceProvider provider : this.persistenceProviders) {
            Persistable result = provider.get(model, index);

            if (result != null && result.exists()) {
                if (result.canMerge()) {

                    outlist.add(result);
                } else {
                    // If it is a non-mergeable result then there is no need to ask another provider again.
                    // return immediately
                    return result;
                }
            }
        }
        Persistable p = outlist.stream().reduce(null, (a, b) -> {
            if (a == null) {
                return b;
            }
            try {
                return a.mergeInto(b);
            } catch (OperationNotSupportedException e) {
                log.error("Error merging data, call canMerge before to see if an object is mergable: ", e);
                return null;
            }
        });
        //For backwards compatibility. Should be solve with issue #1591
        if (p == null) {
            p = (Persistable) model.newInstance();
        }
        return p;
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
     * Updates all {@link PersistenceProvider} and publishes message to all
     * {@link com.iota.iri.zmq.MessageQueueProvider}.
     *
     * @param model with transaction data
     * @param index {@link Hash} identifier of the {@link Transaction} set
     * @param item identifying the purpose of the update
     * @throws Exception when updating the {@link PersistenceProvider} fails
     */
    public void update(Persistable model, Indexable index, String item) throws Exception {
        updatePersistenceProvider(model, index, item);
        updateMessageQueueProvider(model, index, item);
    }

    private void updatePersistenceProvider(Persistable model, Indexable index, String item) throws Exception {
        for(PersistenceProvider provider: this.persistenceProviders) {
                provider.update(model, index, item);
        }
    }

    private void updateMessageQueueProvider(Persistable model, Indexable index, String item) {
        for(MessageQueueProvider provider: this.messageQueueProviders) {
            provider.publishTransaction(model, index, item);
        }
    }

    /**
     * Notifies all registered {@link com.iota.iri.zmq.MessageQueueProvider} and publishes message to MessageQueue.
     *
     * @param message that can be formatted by {@link String#format(String, Object...)}
     * @param objects that should replace the placeholder in message.
     * @see com.iota.iri.zmq.ZmqMessageQueueProvider#publish(String, Object...)
     * @see String#format(String, Object...)
     */
    public void publish(String message, Object... objects) {
        for(MessageQueueProvider provider: this.messageQueueProviders) {
            provider.publish(message, objects);
        }
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

    public <T extends Indexable> List<T> loadAllKeysFromTable(Class<? extends Persistable> modelClass,
                                                                     Function<byte[], T> transformer) {
        List<byte[]> keys = null;
        for(PersistenceProvider provider: this.persistenceProviders) {
            if ((keys = provider.loadAllKeysFromTable(modelClass)) != null) {
                break;
            }
        }

        if (keys != null) {
            return keys.stream()
                    .map(transformer)
                    .collect(Collectors.toList());
        }
        return null;
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
