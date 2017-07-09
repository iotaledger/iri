package com.iota.iri.controllers;

import com.iota.iri.model.Approvee;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Tangle;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/14/17 for iri-testnet.
 */
public class TipsViewModel {
    final Logger log = LoggerFactory.getLogger(TipsViewModel.class);

    private FifoHashCache tips = new FifoHashCache(5000);
    private Set<Hash> solidTips = new HashSet<>();
    private SecureRandom seed = new SecureRandom();
    public final Object sync = new Object();

    public boolean addTipHash (Hash hash) throws ExecutionException, InterruptedException {
        synchronized (sync) {
            return tips.add(hash);
        }
    }

    public boolean removeTipHash (Hash hash) throws ExecutionException, InterruptedException {
        synchronized (sync) {
            if(!tips.remove(hash)) {
                return solidTips.remove(hash);
            }
        }
        return true;
    }

    public void setSolid(Hash tip) {
        synchronized (sync) {
            if(tips.remove(tip)) {
                solidTips.add(tip);
            }
        }
    }

//    public Set<Hash> getTips() {
//        Set<Hash> hashes = new HashSet<>();
//        synchronized (sync) {
//            hashes.addAll(tips);
//            hashes.addAll(solidTips);
//        }
//        return hashes;
//    }
    public Hash getRandomSolidTipHash() {
        synchronized (sync) {
            int size = solidTips.size();
            if(size == 0) {
                return null;
            }
            int index = seed.nextInt(size);
            Iterator<Hash> hashIterator;
            hashIterator = solidTips.iterator();
            Hash hash = null;
            while(index-- > 1 && hashIterator.hasNext()){ hash = hashIterator.next();}
            return hash;
            //return solidTips.size() != 0 ? solidTips.get(seed.nextInt(solidTips.size())) : getRandomNonSolidTipHash();
        }
    }

    public Hash getRandomNonSolidTipHash() {
        synchronized (sync) {
            int size = tips.size();
            if(size == 0) {
                return null;
            }
            int index = seed.nextInt(size);
            Iterator<Hash> hashIterator;
            hashIterator = tips.iterator();
            Hash hash = null;
            while(index-- > 1 && hashIterator.hasNext()){ hash = hashIterator.next();}
            return hash;
            //return tips.size() != 0 ? tips.get(seed.nextInt(tips.size())) : null;
        }
    }

    public Hash getRandomTipHash() throws ExecutionException, InterruptedException {
        synchronized (sync) {
            if(size() == 0) {
                return null;
            }
            int index = seed.nextInt(size());
            if(index >= tips.size()) {
                return getRandomSolidTipHash();
            } else {
                return getRandomNonSolidTipHash();
            }
        }
    }

    public int nonSolidSize() {
        synchronized (sync) {
            return tips.size();
        }
    }

    public int size() {
        return tips.size() + solidTips.size();
    }

    public void loadTipHashes(Tangle tangle) throws Exception {
        Set<Indexable> hashes = tangle.keysWithMissingReferences(Transaction.class, Approvee.class);
        if(hashes != null) {
            for (Indexable h: hashes) {
                tips.add((Hash) h);
            }
        }
    }

    public Set<Hash> getTipsHashesFromDB (Tangle tangle) throws Exception {
        Set<Hash> tipsFromDB = new HashSet<>();
        Set<Indexable> hashes = tangle.keysWithMissingReferences(Transaction.class, Approvee.class);
        if(hashes != null) {
            tipsFromDB.addAll(hashes.stream().map(h -> (Hash) h).collect(Collectors.toList()));
        }
        return tipsFromDB;
    }

    public class FifoHashCache {

        private int capacity;
        private LinkedHashSet<Hash> set;

        public FifoHashCache(int capacity) {
            this.capacity = capacity;
            this.set = new LinkedHashSet<>();
        }

        public boolean add(Hash key) {
            if (this.set.size() == this.capacity) {
                Iterator<Hash> it = this.set.iterator();
                it.next();
                it.remove();
            }
            return set.add(key);
        }
        public boolean remove(Hash key) {
            return this.set.remove(key);
        }
        public int size() {
            return this.set.size();
        }
        public boolean addAll(Collection c) {
            return this.set.addAll(c);
        }
        public Iterator<Hash> iterator() {
            return this.set.iterator();
        }
    }

}
