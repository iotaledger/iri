package com.iota.iri.service.viewModels;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Tip;
import com.iota.iri.service.tangle.Tangle;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by paul on 3/14/17 for iri-testnet.
 */
public class TipsViewModel {

    public static Future<Boolean> addTipHash (Hash hash) throws ExecutionException, InterruptedException {
        Tip tip = new Tip(hash);
        if(!Tangle.instance().maybeHas(tip).get()) {
            return Tangle.instance().save(tip);
        }
        return null;
    }

    public static Future<Void> removeTipHash (Hash hash) throws ExecutionException, InterruptedException {
        Tip tip = new Tip(hash);
        if(Tangle.instance().maybeHas(tip).get()) {
            return Tangle.instance().delete(tip);
        }
        return null;
    }

    public static Hash getRandomTipHash() throws ExecutionException, InterruptedException {
        return ((Hash) Tangle.instance().getLatest(Tip.class).get());
    }
    public static Hash[] getTipHashes() throws ExecutionException, InterruptedException {
        return Arrays.stream(Tangle.instance()
                .keysWithMissingReferences(Tip.class).get())
                .toArray(Hash[]::new);
    }
}
