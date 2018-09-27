package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.model.StateDiff;
import com.iota.iri.storage.Tangle;

import java.util.Map;

public class StateDiffViewModel {
    private StateDiff stateDiff;
    private Hash hash;

    public static StateDiffViewModel load(Tangle tangle, Hash hash) throws Exception {
        return new StateDiffViewModel((StateDiff) tangle.load(StateDiff.class, hash), hash);
    }

    public StateDiffViewModel(final Map<Hash, Long> state, final Hash hash) {
        this.hash = hash;
        this.stateDiff = new StateDiff();
        this.stateDiff.state = state;
    }

    public static boolean maybeExists(Tangle tangle, Hash hash) throws Exception {
        return tangle.maybeHas(StateDiff.class, hash);
    }

    StateDiffViewModel(final StateDiff diff, final Hash hash) {
        this.hash = hash;
        this.stateDiff = diff == null || diff.state == null ? new StateDiff(): diff;
    }

    public boolean isEmpty() {
        return stateDiff == null || stateDiff.state == null || stateDiff.state.size() == 0;
    }

    public Hash getHash() {
        return hash;
    }

    public Map<Hash, Long> getDiff() {
        return stateDiff.state;
    }

    public boolean store(Tangle tangle) throws Exception {
        //return Tangle.instance().save(stateDiff, hash).get();
        return tangle.save(stateDiff, hash);
    }

    public void delete(Tangle tangle) throws Exception {
        tangle.delete(StateDiff.class, hash);
    }
}
