package com.iota.iri.storage;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.StateDiff;
import com.iota.iri.model.persistables.*;
import com.iota.iri.utils.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.iota.iri.zmq.MessageQueueProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by paul on 3/3/17 for iri.
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
    private final List<PermanentPersistenceProvider> permanentPersistenceProviders = new ArrayList<>();
    private final List<MessageQueueProvider> messageQueueProviders = new ArrayList<>();

    public void addPersistenceProvider(PersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }
    public void addPermanentPersistenceProvider(PermanentPersistenceProvider provider) {
        this.permanentPersistenceProviders.add(provider);
    }

    /**
     * Adds {@link com.iota.iri.storage.MessageQueueProvider} that should be notified.
     * @param provider that should be notified.
     */
    public void addMessageQueueProvider(MessageQueueProvider provider) {
        this.messageQueueProviders.add(provider);
    }

    public void init() throws Exception {
        for(PersistenceProvider provider: this.persistenceProviders) {
            provider.init();
        }
    }

    public void shutdown() throws Exception {
        log.info("Shutting down Tangle Persistence Providers... ");
        this.persistenceProviders.forEach(PersistenceProvider::shutdown);
        this.persistenceProviders.clear();
        log.info("Shutting down Tangle MessageQueue Providers... ");
        this.messageQueueProviders.forEach(MessageQueueProvider::shutdown);
        this.messageQueueProviders.clear();
    }

    public Persistable load(Class<?> model, Indexable index) throws Exception {
            LinkedList<Persistable> outlist = new LinkedList<>();
            for(PersistenceProvider provider: this.persistenceProviders) {
                Persistable result =  provider.get(model, index);

                if (result != null && !result.isEmpty()) {
                    if(result.merge()){

                        outlist.add(result);
                    }else{
                        //If it is a non-mergeable result then there is no need to ask another provider again.
                        // Return immediately
                        return result;
                    }
                }
            }
            Persistable p = outlist.stream().reduce(null, (a,b) -> {
                if(a == null){
                    return b;
                }
                return a.mergeTwo(b);
            });
            if(p==null){
                p =  (Persistable) model.newInstance();
            }

            return p;

    }

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

    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.deleteBatch(models);
        }
    }

    public void delete(Class<?> model, Indexable index) throws Exception {
            for(PersistenceProvider provider: persistenceProviders) {
                provider.delete(model, index);
            }
    }

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
     * Updates all {@link PersistenceProvider} and publishes message to all {@link com.iota.iri.storage.MessageQueueProvider}.
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
     * Notifies all registered {@link com.iota.iri.storage.MessageQueueProvider} and publishes message to MessageQueue.
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

    public Boolean exists(Class<?> modelClass, Indexable hash) throws Exception {
            for(PersistenceProvider provider: this.persistenceProviders) {
                if (provider.exists(modelClass, hash)) {
                    return true;
                }
            }
            return false;
    }

    public Boolean maybeHas(Class<?> model, Indexable index) throws Exception {
            for(PersistenceProvider provider: this.persistenceProviders) {
                if (provider.mayExist(model, index)) {
                    return true;
                }
            }
            return false;
    }

    public Long getCount(Class<?> modelClass) throws Exception {
            long value = 0;
            for(PersistenceProvider provider: this.persistenceProviders) {
                if((value = provider.count(modelClass)) != 0) {
                    break;
                }
            }
            return value;
    }

    public Persistable find(Class<?> model, byte[] key) throws Exception {
            Persistable out = null;
            for (PersistenceProvider provider : this.persistenceProviders) {
                if ((out = provider.seek(model, key)) != null) {
                    break;
                }
            }
            return out;
    }

    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if(latest == null) {
                    latest = provider.next(model, index);
                }
            }
            return latest;
    }

    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if(latest == null) {
                    latest = provider.previous(model, index);
                }
            }
            return latest;
    }

    public Pair<Indexable, Persistable > getFirst(Class<?> model, Class<?> index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if(latest == null) {
                    latest = provider.first(model, index);
                }
            }
            return latest;
    }

    public void clearColumn(Class<?> column) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.clear(column);
        }
    }

    public void clearMetadata(Class<?> column) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.clearMetadata(column);
        }
    }

    // ---------- Permanent storage capabilities --------


    public void pinTransaction(TransactionViewModel tvm)  throws Exception {
        for(PermanentPersistenceProvider provider: permanentPersistenceProviders) {
            provider.saveTransaction(tvm, tvm.getHash());
        }
    }

    public void pinTransaction(Hash hash)  throws Exception {
        Transaction tx = (Transaction)load(Transaction.class, hash);
        if(!tx.isEmpty()) {
            TransactionViewModel tvm = new TransactionViewModel(tx, hash);
            pinTransaction(tvm);
        }
    }

    public void incrementTransactions(Indexable[] indexes) throws Exception {
        for(PermanentPersistenceProvider provider: permanentPersistenceProviders) {
            provider.incrementTransactions(indexes);
        }
    }

    public void decrementTransactions(Indexable[] indexes) throws Exception {
        for(PermanentPersistenceProvider provider: permanentPersistenceProviders) {
            provider.decrementTransactions(indexes);
        }
    }

    public long getTransactionStorageCounter(Hash index) throws Exception {
        long toReturn  = 0;
        for(PermanentPersistenceProvider provider: permanentPersistenceProviders) {
            toReturn = Math.max(0, provider.getCounter(index));
        }
        return toReturn;
    }




    // -------------------------------------------------


}
