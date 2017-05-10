package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Tip;
import com.iota.iri.storage.Tangle;
import org.apache.commons.lang3.ArrayUtils;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Created by paul on 3/14/17 for iri-testnet.
 */
public class TipsViewModel {

    private static Set<Hash> tips = new HashSet<>();
    private static Set<Hash> solidTips = new HashSet<>();
    private static SecureRandom seed = new SecureRandom();
    public static final Object sync = new Object();

    public static boolean addTipHash (Hash hash) throws ExecutionException, InterruptedException {
        synchronized (sync) {
            return tips.add(hash);
        }
    }

    public static boolean removeTipHash (Hash hash) throws ExecutionException, InterruptedException {
        synchronized (sync) {
            if(!tips.remove(hash)) {
                return solidTips.remove(hash);
            }
        }
        return true;
    }

    public static void setSolid(Hash tip) {
        synchronized (sync) {
            if(!tips.remove(tip)) {
                solidTips.add(tip);
            }
        }
    }

    public static Set<Hash> getTips() {
        Set<Hash> hashes = new HashSet<>();
        synchronized (sync) {
            hashes.addAll(tips);
            hashes.addAll(solidTips);
        }
        return hashes;
    }
    public static Hash getRandomSolidTipHash() {
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

    public static Hash getRandomNonSolidTipHash() {
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

    public static Hash getRandomTipHash() throws ExecutionException, InterruptedException {
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

    public static int nonSolidSize() {
        return tips.size();
    }

    public static int size() {
        return tips.size() + solidTips.size();
    }

    public static void loadTipHashes() throws ExecutionException, InterruptedException {
        Hash[] hashes = Arrays.stream(Tangle.instance()
                .keysWithMissingReferences(Tip.class).get())
                .toArray(Hash[]::new);
        tips.addAll(Arrays.asList(hashes));
    }
}
