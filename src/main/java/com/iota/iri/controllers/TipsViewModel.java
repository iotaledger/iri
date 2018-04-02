package com.iota.iri.controllers;

import com.iota.iri.model.Hash;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class TipsViewModel {

    // THIS NEEDS TO BE PACKAGE LEVEL ACCESS FOR TESTS ONLY
    public static final int MAX_TIPS = 5000;

    private final FifoHashCache<Hash> tips = new FifoHashCache<>(TipsViewModel.MAX_TIPS);
    private final FifoHashCache<Hash> solidTips = new FifoHashCache<>(TipsViewModel.MAX_TIPS);

    private final SecureRandom seed = new SecureRandom();
    private final Object sync = new Object();

    /**
     * @param hash The tip to add.
     * @return true If the {hash} was added to {@code tips} and {@code tips} did not already contain it.
     */
    public boolean addTipHash(Hash hash) {
        synchronized (sync) {
            return tips.add(hash);
        }
    }

    /**
     * @param hash The tip to remove.
     * @return true If the tip was removed from either {@code tips} or {@code solidTips}.
     */
    public boolean removeTipHash(Hash hash) {
        synchronized (sync) {
            return tips.remove(hash) || solidTips.remove(hash);
        }
    }

    /**
     * This removes the tip from {@code tips} and adds it to {@code solidTips}.
     *
     * @param tip The tip to set solid.
     * @return true If the tip was removed from {@code tips} and added to {@code solidTips}.
     */
    public boolean setSolid(Hash tip) {
        synchronized (sync) {
            return tips.remove(tip) && solidTips.add(tip);
        }
    }

    public Set<Hash> getTips() {
        synchronized (sync) {
            Set<Hash> hashes = new HashSet<>(tips.size() + solidTips.size());
            tips.iterator().forEachRemaining(hashes::add);
            solidTips.iterator().forEachRemaining(hashes::add);
            return hashes;
        }
    }

    public Hash getRandomSolidTipHash() {
        synchronized (sync) {
            int size = solidTips.size();
            if (size == 0) {
                return getRandomNonSolidTipHash();
            }
            int index = seed.nextInt(size);
            Iterator<Hash> hashIterator = solidTips.iterator();
            Hash hash = null;
            while (index-- >= 0 && hashIterator.hasNext()) {
                hash = hashIterator.next();
            }
            return hash;
        }
    }

    public Hash getRandomNonSolidTipHash() {
        synchronized (sync) {
            int size = tips.size();
            if (size == 0) {
                return null;
            }
            int index = seed.nextInt(size);
            Iterator<Hash> hashIterator = tips.iterator();
            Hash hash = null;
            while (index-- >= 0 && hashIterator.hasNext()) {
                hash = hashIterator.next();
            }
            return hash;
        }
    }

    public int nonSolidSize() {
        synchronized (sync) {
            return tips.size();
        }
    }

    public int size() {
        synchronized (sync) {
            return tips.size() + solidTips.size();
        }
    }

    private class FifoHashCache<K> {

        private final int capacity;
        private final LinkedHashSet<K> set;

        public FifoHashCache(int capacity) {
            this.capacity = capacity;
            this.set = new LinkedHashSet<>();
        }

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

        public boolean remove(K key) {
            return this.set.remove(key);
        }

        public int size() {
            return this.set.size();
        }

        public Iterator<K> iterator() {
            return this.set.iterator();
        }
    }

}