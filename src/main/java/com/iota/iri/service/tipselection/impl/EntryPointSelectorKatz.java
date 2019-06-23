package com.iota.iri.service.tipselection.impl;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.HashMap;


/**
 * This is different from the traditional entry point selector, because it ignores the milestone.
 */
public class EntryPointSelectorKatz implements EntryPointSelector {

    private Tangle tangle;
    private static final Logger log = LoggerFactory.getLogger(EntryPointSelectorKatz.class);

    public EntryPointSelectorKatz(Tangle tangle, HashMap<Hash, String> nMap) {
        this.tangle = tangle;
    }

    @Override
    public Hash getEntryPoint(int depth) {
        Hash ret;
        if(BaseIotaConfig.getInstance().getStreamingGraphSupport()) {
            ret = tangle.getMaxScoreHashOnLevel(depth);
        } else {
            tangle.buildGraph();
            try {
                tangle.computeScore();
            } catch(Exception e) {
                e.printStackTrace(new PrintStream(System.out));
            }
            ret = tangle.getMaxScoreHashOnLevel(depth);
        }
        return ret;
    }
}
