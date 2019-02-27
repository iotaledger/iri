package com.iota.iri.storage;

import com.iota.iri.utils.Pair;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Abstracts access to the data persisitence layer.
 * Handles the initialization and connections to the storage.
 * Will enable efficient batch writes, deletes, and merges.
 */
public interface PersistenceProvider {

    /**
     * Will setup and configure the DB. Including initializing of heavyweight
     * connections, rules on how to communicate with the DB schema, how to sync with the DB,
     * how to backup the data and so on.
     * @throws Exception if there was an error communicating with the DB
     */
    void init() throws Exception;

    //To be deleted
    boolean isAvailable();

    /**
     * Free all the resources that are required to communicate with the DB
     */
    void shutdown();

    /**
     * Persists the {@code model} to storage.
     *
     * @param model what we persist
     * @param index the primary key that we use to store
     * @return <tt>true</tt> if successful, else <tt>false</tt>
     * @throws Exception if we encounter a problem with the DB
     */
    boolean save(Persistable model, Indexable index) throws Exception;

    /**
     * Deletes the value that is stored at a specific {@code index} from the DB.
     * @param model defines the type of object we want to delete
     * @param index the key we delete at
     * @throws Exception if we encounter a problem with the DB
     */
    void delete(Class<?> model, Indexable  index) throws Exception;

    /**
     * Updates metadata on a certain index
     *
     * @param model container of the metadata we need
     * @param index the key where we store the metadata
     * @param item extra piece of metadata
     * @return <tt>true</tt> if successful, else <tt>false</tt>
     * @throws Exception if we encounter a problem with the DB
     */
    boolean update(Persistable model, Indexable index, String item) throws Exception;

    /**
     * Ensures that the object of type {@code model} at {@code key} is stored in the DB.
     *
     * @param model specifies in what table/column family to look for the object
     * @param key the key we expect to find in the db
     * @return <tt>true</tt> if the object is in the DB, else <tt>false</tt>
     * @throws Exception if we encounter a problem with the DB
     */
    boolean exists(Class<?> model, Indexable key) throws Exception;

    /**
     * Returns the latest object of type {@code model} that was persisted with a key of type {@code indexModel}
     * @param model specifies in what table/column family to look
     * @param indexModel specifies the type of the key
     * @return the (key,value) pair of the found object
     *
     * @throws Exception if we encounter a problem with the DB
     */
    Pair<Indexable, Persistable> latest(Class<?> model, Class<?> indexModel) throws Exception;

    /**
     * Given 2 {@link Persistable} types that should be indexed with the same keys,
     * find all the keys in the database that are in the {@code modelClass} table/column family
     * but are not in the {@code otherClass} table/column family.
     *
     * @param modelClass a persistable type
     * @param otherClass a persistable type that should have the same keys as {@code modelClass}
     * @return The keys of {@code modelClass} that don't index {@code otherClass} models
     * @throws Exception
     */
    Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> otherClass) throws Exception;

    /**
     * Retrieves a {@code model} type indexed at with {@code index} key
     * @param model the table/column family to look at
     * @param index the key
     * @return The stored value
     * @throws Exception if we encounter a problem with the DB
     */
    Persistable get(Class<?> model, Indexable index) throws Exception;

    /**
     * Checks with a degree of certainity whether we have a value indexed at a
     * given key. Unlike {@link #exists} it can return false positives, but it should be much more efficient
     *
     * @param model the table/column family to look at
     * @param index the key
     * @return <tt>true</tt> if might exist, else <tt>false</tt> if definitely not in the DB
     * @throws Exception if we encounter a problem with the DB
     */
    boolean mayExist(Class<?> model, Indexable index) throws Exception;

    /**
     * Counts how many instances of type {@code model} are stored in the DB.
     * The count may be an estimated figure.
     *
     * @param model the table/column family to look at
     * @return an estimated count of the members at the above table/column family
     * @throws Exception if we encounter a problem with the DB
     */
    long count(Class<?> model) throws Exception;

    /**
     * Gets all the keys starting with {@code value} prefix that index type {@code modelClass}.
     * @param modelClass the table/column family to look at
     * @param value the key prefix in bytes
     * @return keys that have {@code value} as their prefix
     */
    Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value);

    /**
     * Gets the first object persisted with a key that has {@code key} prefix.
     * @param model the table/column family to look at
     * @param key the key prefix in bytes
     * @return the first Persistable that was found
     * @throws Exception if we encounter a problem with the DB
     */
    Persistable seek(Class<?> model, byte[] key) throws Exception;

    /**
     * Finds the next object persisted after the key {code index}
     * @param model the table/column family to look at
     * @param index the key from which we start scanning forward
     * @return a pair (key, value)
     * @throws Exception if we encounter a problem with the DB
     */
    Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception;

    /**
     * Finds the previous object persisted after the key {code index}
     * @param model the table/column family to look at
     * @param index the key from which we start scanning backwards
     * @return a pair (key, value)
     * @throws Exception if we encounter a problem with the DB
     */
    Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception;

    /**
     * Finds the first persisted model of type {@code model} that has a key of type {@code indexModel}
     * @param model the table/column family to look at
     * @param indexModel the index type
     * @return a pair (key, value)
     * @throws Exception if we encounter a problem with the DB
     */
    Pair<Indexable, Persistable> first(Class<?> model, Class<?> indexModel) throws Exception;

    /**
     * Atomically saves all {@code models}
     *
     * @param models key-value pairs that to be stored in the db
     * @return <tt>true</tt> if successful, <tt>else</tt> returns false
     * @throws Exception if we encounter a problem with the DB
     */
    boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception;

    /**

     * Atomically delete all {@code models}.
     * @param models key value pairs that to be expunged from the db.
     * @throws Exception if data could not be expunged from the db.
     */
    void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception;

    /**
     * Clear all the data (but not metadata) in a column family or a table
     * @param column the table/column family we clear
     * @throws Exception if we encounter a problem with the DB
     */
    void clear(Class<?> column) throws Exception;

    /**
     * Clear all the metadata in a column family or a table
     * @param column the table/column family we clear the metadata from
     * @throws Exception if we encounter a problem with the DB
     */
    void clearMetadata(Class<?> column) throws Exception;

    List<byte[]> loadAllKeysFromTable(Class<? extends Persistable> model);
}
