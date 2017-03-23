package com.iota.iri.service.viewModels;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Tip;
import com.iota.iri.service.tangle.Tangle;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by paul on 3/14/17 for iri-testnet.
 */
public class TipsViewModel {

    public static Future<Boolean> addTipHash (BigInteger hash) throws ExecutionException, InterruptedException {
        Tip tip = new Tip(hash);
        if(!Tangle.instance().maybeHas(tip).get()) {
            return Tangle.instance().save(tip);
        }
        return null;
    }

    public static Future<Void> removeTipHash (BigInteger hash) throws ExecutionException, InterruptedException {
        Tip tip = new Tip(hash);
        if(Tangle.instance().maybeHas(tip).get()) {
            return Tangle.instance().delete(tip);
        }
        return null;
    }

    public static BigInteger[] getTipHashes() throws ExecutionException, InterruptedException {
        return Arrays.stream(Tangle.instance()
                .loadAll(Tip.class).get())
                .map(tip -> ((Tip) tip).hash)
                .toArray(BigInteger[]::new);
    }
}
