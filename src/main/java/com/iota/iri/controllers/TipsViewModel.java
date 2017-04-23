package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Tip;
import com.iota.iri.storage.Tangle;

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
    private static SecureRandom seed = new SecureRandom();

    public static boolean addTipHash (Hash hash) throws ExecutionException, InterruptedException {
        synchronized (tips) {
            return tips.add(hash);
        }
    }

    public static boolean removeTipHash (Hash hash) throws ExecutionException, InterruptedException {
        synchronized (tips) {
            return tips.remove(hash);
        }
    }

    public static List<Hash> getTips() {
        return tips.stream().collect(Collectors.toList());
    }

    public static Hash getRandomTipHash() throws ExecutionException, InterruptedException {
        return (Hash) tips.toArray()[seed.nextInt(size())];
    }

    public static int size() {
        return tips.size();
    }

    public static void loadTipHashes() throws ExecutionException, InterruptedException {
        Hash[] hashes = Arrays.stream(Tangle.instance()
                .keysWithMissingReferences(Tip.class).get())
                .toArray(Hash[]::new);
        tips.addAll(Arrays.asList(hashes));
    }
}
