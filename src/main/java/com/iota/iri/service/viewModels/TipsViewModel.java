package com.iota.iri.service.viewModels;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Tip;
import com.iota.iri.service.tangle.Tangle;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/14/17 for iri-testnet.
 */
public class TipsViewModel {

    public static void addTipHash (byte[] hash) throws ExecutionException, InterruptedException {
        if(!Tangle.instance().maybeHas(Tip.class, hash).get()) {
            Tip tip = new Tip();
            tip.hash = hash;
            Tangle.instance().save(tip).get();
        }
    }

    public static void removeTipHash (byte[] hash) throws ExecutionException, InterruptedException {
        if(Tangle.instance().maybeHas(Tip.class, hash).get()) {
            Tip tip = new Tip();
            tip.hash = hash;
            Tangle.instance().delete(tip).get();
        }
    }

    public static Hash[] getTipHashes() throws ExecutionException, InterruptedException {
        return Arrays.stream(Tangle.instance().loadAll(Tip.class).get()).map(tip -> new Hash(((Tip) tip).hash)).toArray(Hash[]::new);
    }
}
