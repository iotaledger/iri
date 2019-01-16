package com.iota.iri.service.tipselection.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.localinmemorygraph.LocalInMemoryGraphProvider;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.utils.Pair;

import java.util.*;
import java.io.PrintStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is different from the traditional entry point selector, because it ignores the milestone.
 */
public class EntryPointSelectorKatz implements EntryPointSelector {

    private LocalInMemoryGraphProvider localGraph;
    private static final Logger log = LoggerFactory.getLogger(EntryPointSelectorKatz.class);

    public EntryPointSelectorKatz(Tangle tangle, HashMap<Hash, String> nMap) {
        localGraph = (LocalInMemoryGraphProvider)tangle.getPersistenceProvider("LOCAL_GRAPH");
    }

    @Override
    public Hash getEntryPoint(int depth) throws Exception {
        Hash ret = null;
        if(BaseIotaConfig.getInstance().getStreamingGraphSupport()) {
            ret = localGraph.getPivotalHash(depth);
        } else {
            
            localGraph.buildGraph();
            
            try {
                
                KatzCentrality centrality = new KatzCentrality(LocalInMemoryGraphProvider.graph, 0.5);
                LocalInMemoryGraphProvider.score = centrality.compute();
                
            } catch(Exception e) {
                e.printStackTrace(new PrintStream(System.out));
            }
            ret = LocalInMemoryGraphProvider.getPivotalHash(depth);
        }
        return ret;
    }
}
