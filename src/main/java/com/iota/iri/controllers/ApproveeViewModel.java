package com.iota.iri.controllers;

import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheConfiguration;
import com.iota.iri.model.Hash;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Approvee;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import java.util.Queue;
import java.util.Set;

/**
 * Acts as a controller interface for an {@link Approvee} set. This controller is used within a
 * {@link TransactionViewModel} to manipulate an {@link Approvee} set.
 */
public class ApproveeViewModel implements HashesViewModel {
    private Approvee self;
    private Indexable hash;

    /**
     * Constructor for an {@link Approvee} set controller from a {@link Hash} identifier.
     * @param hash The {@link Hash} identifier that the controller will be created for.
     */
    public ApproveeViewModel(Hash hash) {
        this.hash = hash;
    }

    /**
     * Constructor for an {@link Approvee} set from an existing {@link ApproveeViewModel}
     * 
     * @param approveeViewModel ApproveeViewModel
     */
    public ApproveeViewModel(ApproveeViewModel approveeViewModel) {
        this.hash = approveeViewModel.hash;
        this.self = new Approvee();
        approveeViewModel.getHashes().forEach(hash1 -> addHash(hash1));
    }

    /**
     * Constructor for an {@link Approvee} set controller from an existing {@link Approvee} set. If the set is empty, a
     * new {@link Approvee} set is created.
     *
     * @param hashes The {@link Approvee} set that the controller will be created from
     * @param hash The {@link Hash} identifier that acts as a reference for the {@link Approvee} set
     */
    public ApproveeViewModel(Approvee hashes, Indexable hash) {
        self = hashes == null || hashes.set == null ? new Approvee(): hashes;
        this.hash = hash;
    }

    /**
     * Creates a new {@link Approvee} set controller. This controller is created by extracting the {@link Approvee} set
     * from the database using the provided {@link Hash} identifier.
     *
     * @param tangle The tangle reference for the database to find the {@link Approvee} set in
     * @param hash The hash identifier for the {@link Approvee} set that needs to be found
     * @return The {@link ApproveeViewModel} controller generated
     * @throws Exception Thrown if the database cannot load an {@link Approvee} set from the reference {@link Hash}
     */
    public static ApproveeViewModel load(Tangle tangle, Indexable hash) throws Exception {
        Cache<Indexable, ApproveeViewModel> cache = tangle.getCache(ApproveeViewModel.class);
        ApproveeViewModel approveeViewModel;
        if (cache != null) {
            approveeViewModel = cache.get(hash);
            if (approveeViewModel != null) {
                return new ApproveeViewModel(approveeViewModel);
            }
        }

        approveeViewModel = new ApproveeViewModel((Approvee) tangle.load(Approvee.class, hash), hash);
        if (cache != null && approveeViewModel.getHashes().size() > 0) {
            cachePut(tangle, approveeViewModel, hash);
        }

        return approveeViewModel;
    }

    /**
     * Fetches the first persistable {@link Approvee} set from the database and generates a new
     * {@link ApproveeViewModel} from it. If no {@link Approvee} sets exist in the database, it will return null.
     *
     * @param tangle the tangle reference for the database
     * @return The new {@link ApproveeViewModel}
     * @throws Exception Thrown if the database fails to return a first object
     */
    public static ApproveeViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.getFirst(Approvee.class, TransactionHash.class);
        if(bundlePair != null && bundlePair.hi != null) {
            return new ApproveeViewModel((Approvee) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    @Override
    public boolean store(Tangle tangle) throws Exception {
        Cache<Indexable, ApproveeViewModel> cache = tangle.getCache(ApproveeViewModel.class);
        ApproveeViewModel approveeViewModel = cache.get(hash);
        if (approveeViewModel != null) {
            return true;
        }

        cachePut(tangle, this, hash);
        return true;
    }

    @Override
    public int size() {
        return self.set.size();
    }

    @Override
    public boolean addHash(Hash theHash) {
        return getHashes().add(theHash);
    }

    @Override
    public Indexable getIndex() {
        return hash;
    }

    @Override
    public Set<Hash> getHashes() {
        return self.set;
    }

    @Override
    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Approvee.class,hash);
        cacheDelete(tangle, hash);
    }

    @Override
    public ApproveeViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> bundlePair = tangle.next(Approvee.class, hash);
        if(bundlePair != null && bundlePair.hi != null) {
            return new ApproveeViewModel((Approvee) bundlePair.hi, (Hash) bundlePair.low);
        }
        return null;
    }

    /**
     * Puts the approvee in cache
     * 
     * @param tangle            Tangle
     * @param approveeViewModel The approveeViewModel to cache
     * @param hash              The hash of this viewmodel
     */
    public static void cachePut(Tangle tangle, ApproveeViewModel approveeViewModel, Indexable hash) throws Exception {
        Cache<Indexable, ApproveeViewModel> cache = tangle.getCache(ApproveeViewModel.class);
        if (cache.getSize() >= cache.getConfiguration().getMaxSize()) {
            cacheRelease(tangle);
        }
        cache.put(hash, approveeViewModel);
    }

    /**
     * Deletes the item with the specified hash fro the cache. Delegates to {@link Cache#delete(Object)}
     *
     * @param tangle Tangle
     * @param hash   Hash of the item to delete
     */
    private static void cacheDelete(Tangle tangle, Indexable hash) {
        Cache<Indexable, ApproveeViewModel> cache = tangle.getCache(ApproveeViewModel.class);
        if (cache != null) {
            cache.delete(hash);
        }
    }

    /**
     * Release {@link CacheConfiguration#getReleaseCount()} items from cache. Since this data is immutable, we only
     * release from memory and not persist to DB again.
     * 
     * @param tangle Tangle
     * @throws Exception Exception
     */
    private static void cacheRelease(Tangle tangle) throws Exception {
        Cache<Indexable, ApproveeViewModel> cache = tangle.getCache(ApproveeViewModel.class);
        Queue<Indexable> releaseQueueCopy = cache.getReleaseQueueCopy();

        for (int i = 0; i < cache.getConfiguration().getReleaseCount(); i++) {
            Indexable hash = releaseQueueCopy.poll();
            if (hash != null) {
                ApproveeViewModel approveeViewModel = cache.get(hash);
                if (approveeViewModel != null) {
                    cache.release(hash);
                }
            }
        }
    }
}
