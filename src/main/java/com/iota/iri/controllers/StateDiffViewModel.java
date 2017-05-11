package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.StateDiff;
import com.iota.iri.storage.Tangle;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 5/6/17.
 */
public class StateDiffViewModel {
    private StateDiff stateDiff;
    private Hash hash;

    public static StateDiffViewModel load(Hash hash) throws Exception {
        return new StateDiffViewModel((StateDiff) Tangle.instance().load(StateDiff.class, hash), hash);
    }

    public StateDiffViewModel(final Map<Hash, Long> state, final Hash hash) {
        this.hash = hash;
        this.stateDiff = new StateDiff();
        this.stateDiff.state = state;
    }

    public static boolean exists(Hash hash) throws Exception {
        return Tangle.instance().maybeHas(StateDiff.class, hash);
    }

    StateDiffViewModel(final StateDiff diff, final Hash hash) {
        this.hash = hash;
        this.stateDiff = diff == null || diff.state == null ? new StateDiff(): diff;
    }

    public Hash getHash() {
        return hash;
    }

    public Map<Hash, Long> getDiff() {
        return stateDiff.state;
    }

    public boolean store() throws Exception {
        //return Tangle.instance().save(stateDiff, hash).get();
        return Tangle.instance().save(stateDiff, hash);
    }

    void delete() throws Exception {
        Tangle.instance().delete(StateDiff.class, hash);
    }
}
