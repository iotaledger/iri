package com.iota.iri.controllers;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import com.iota.iri.model.Hash;

/**
 * Acts as a controller interface for a <tt>Tips</tt> set. A tips set is a a First In First Out cache for
 * {@link com.iota.iri.model.persistables.Transaction} objects that have no children. <tt>Tips</tt> are stored in the
 * {@link TipsViewModel} until they are deemed solid or are removed from the cache.
 */
public class TipsViewModel {

    /** The maximum size of the <tt>Tips</tt> set*/
    public static final int MAX_TIPS = 5000;

    private final FifoHashCache<Hash> tips = new FifoHashCache<>(TipsViewModel.MAX_TIPS);
    private final FifoHashCache<Hash> solidTips = new FifoHashCache<>(TipsViewModel.MAX_TIPS);

    private final SecureRandom seed = new SecureRandom();
    private final Object sync = new Object();

    /**
     * Adds a {@link Hash} object to the tip cache in a synchronous fashion.
     *
     * @param hash The {@link Hash} identifier of the object to be added
     */
    public void addTipHash(Hash hash) {
        synchronized (sync) {
            tips.add(hash);
        }
    }

    /**
     * Removes a {@link Hash} object from the tip cache in a synchronous fashion.
     *
     * @param hash The {@link Hash} identifier of the object to be removed
     */
    public void removeTipHash(Hash hash) {
        synchronized (sync) {
            if (!tips.remove(hash)) {
                solidTips.remove(hash);
            }
        }
    }

    /**
     * Removes the referenced {@link Hash} object from the <tt>Tips</tt> cache and adds it to the <tt>SolidTips</tt>
     * cache.
     *
     * <p>
     *     A solid tip is a transaction that has been stored in the database, and there are no missing transactions in its history.
     * </p>
     *
     * @param tip The {@link Hash} identifier for the object that will be set to solid
     */
    public void setSolid(Hash tip) {
        synchronized (sync) {
            if (tips.remove(tip)) {
                solidTips.add(tip);
            }
        }
    }

    /**
     * Iterates through all solid and non-solid tips and compiles them into one {@link Hash} set to be returned. This
     * does so in a synchronised fashion.
     *
     * @return The {@link Hash} set containing all solid and non-solid tips
     */
    public Set<Hash> getTips() {
        Set<Hash> hashes = new HashSet<>();
        synchronized (sync) {
            Iterator<Hash> hashIterator;
            hashIterator = tips.iterator();
            while (hashIterator.hasNext()) {
                hashes.add(hashIterator.next());
            }

            hashIterator = solidTips.iterator();
            while (hashIterator.hasNext()) {
                hashes.add(hashIterator.next());
            }
        }
        return hashes;
    }

    /**
     * Returns a random tip by generating a random integer within the range of the <tt>SolidTips</tt> set, and iterates
     * through the set until a hash is returned. If there are no <tt>Solid</tt> tips available, then
     * <tt>getRandomNonSolidTipHash</tt> is called and returned instead.
     *
     * @return A random <tt>Solid</tt> tip if available, a random <tt>NonSolid</tt> tip if not
     */
    public Hash getRandomSolidTipHash() {
        synchronized (sync) {
            int size = solidTips.size();
            if (size == 0) {
                return getRandomNonSolidTipHash();
            }
            int index = seed.nextInt(size);
            Iterator<Hash> hashIterator;
            hashIterator = solidTips.iterator();
            Hash hash = null;
            while (index-- >= 0 && hashIterator.hasNext()) {
                hash = hashIterator.next();
            }
            return hash;
            //return solidTips.size() != 0 ? solidTips.get(seed.nextInt(solidTips.size())) : getRandomNonSolidTipHash();
        }
    }

    /**
     * Returns a random tip by generating integer within the range of the <tt>Tips</tt> set, and iterates through the
     * set until a hash is returned. If there are no tips available, then null is returned instead.
     *
     * @return A random tip if available, null if not
     */
    public Hash getRandomNonSolidTipHash() {
        synchronized (sync) {
            int size = tips.size();
            if (size == 0) {
                return null;
            }
            int index = seed.nextInt(size);
            Iterator<Hash> hashIterator;
            hashIterator = tips.iterator();
            Hash hash = null;
            while (index-- >= 0 && hashIterator.hasNext()) {
                hash = hashIterator.next();
            }
            return hash;
            //return tips.size() != 0 ? tips.get(seed.nextInt(tips.size())) : null;
        }
    }

    /**
     * Fetches the size of the <tt>Tips</tt> set in a synchronised fashion
     * @return The size of the set
     */
    public int nonSolidSize() {
        synchronized (sync) {
            return tips.size();
        }
    }

    /**
     * Fetches the size of the <tt>SolidTips</tt> set in a synchronised fashion
     * @return The size of the set
     */
    public int solidSize() {
        synchronized (sync) {
            return solidTips.size();
        }
    }

    /**
     * Fetches the size of the <tt>Tips</tt> set and <tt>SolidTips</tt>set combined. This does so in a synchronised
     * fashion.
     * @return The size of both sets combined
     */
    public int size() {
        synchronized (sync) {
            return tips.size() + solidTips.size();
        }
    }

    /**
     * A First In First Out hash set for storing <tt>Tip</tt> transactions.
     *
     * @param <K> The class of object that will be stored in the hash set
     */
    private class FifoHashCache<K> {

        private final int capacity;
        private final LinkedHashSet<K> set;

        /**
         * Constructor for a <tt>Fifo LinkedHashSet Hash</tt> set of a given size.
         *
         * @param capacity The maximum size allocated for the set
         */
        public FifoHashCache(int capacity) {
            this.capacity = capacity;
            this.set = new LinkedHashSet<>();
        }

        /**
         * Determines if there is vacant space available in the set, and adds the provided object to the set if so. If
         * there is no space available, the set removes the next available object iteratively until there is room
         * available.
         *
         * @param key The {@link Hash} identifier for the object that will be added to the set
         * @return True if the new objects have been added, False if not
         */
        public boolean add(K key) {
            int vacancy = this.capacity - this.set.size();
            if (vacancy <= 0) {
                Iterator<K> it = this.set.iterator();
                for (int i = vacancy; i <= 0; i++) {
                    it.next();
                    it.remove();
                }
            }
            return this.set.add(key);
        }

        /**
         * Removes the referenced object from the set.
         *
         * @param key The {@link Hash} identifier for the object that will be removed from the set
         * @return True if the object is removed, False if not
         */
        public boolean remove(K key) {
            return this.set.remove(key);
        }

        /**@return The integer size of the stored {@link Hash} set*/
        public int size() {
            return this.set.size();
        }

        /**
         * Creates a new iterator for the object set based on the {@link Hash} class of the set.
         * @return The class matched iterator for the stored set*/
        public Iterator<K> iterator() {
            return this.set.iterator();
        }
    }

}
