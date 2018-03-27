package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/14/17 for iri-testnet.
 */
public class TipsViewModel {
    private static final Logger log = LoggerFactory.getLogger(TipsViewModel.class);

    // THIS NEEDS TO BE PACKAGE LEVEL ACCESS FOR TESTS ONLY
    public static final int MAX_TIPS = 5000;

    private final FifoHashCache<Hash> tips = new FifoHashCache<>(TipsViewModel.MAX_TIPS);
    private final FifoHashCache<Hash> solidTips = new FifoHashCache<>(TipsViewModel.MAX_TIPS);

    private final SecureRandom seed = new SecureRandom();
    private final Object sync = new Object();

    public void addTipHash(Hash hash) throws ExecutionException, InterruptedException {
        synchronized (sync) {
            tips.add(hash);
        }
    }

    public void removeTipHash(Hash hash) throws ExecutionException, InterruptedException {
        synchronized (sync) {
            if (!tips.remove(hash)) {
                solidTips.remove(hash);
            }
        }
    }

    public void setSolid(Hash tip) {
        synchronized (sync) {
            if (tips.remove(tip)) {
                solidTips.add(tip);
            }
        }
    }

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