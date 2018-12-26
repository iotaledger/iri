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


/**
 * This is different from the traditional entry point selector, because it ignores the milestone.
 */
public class EntryPointSelectorKatz implements EntryPointSelector {

    private final Tangle tangle;
    private LocalInMemoryGraphProvider localGraph;

    public HashMap<Hash, Double> score;
    HashMap<Hash, Set<Hash>> graph;
    HashMap<Hash, Set<Hash>> revGraph;
    HashMap<Hash, Integer> degs;
    HashMap<Integer, Set<Hash>> topOrder;
    HashMap<Hash, String> nameMap;
    int totalDepth;

    public EntryPointSelectorKatz(Tangle tangle, HashMap<Hash, String> nMap) {
        this.tangle = tangle;
        
        graph = new HashMap<>();
        revGraph = new HashMap<>();
        degs = new HashMap<>();
        topOrder = new HashMap<>();
        totalDepth = 0;
        this.nameMap = nMap;
        localGraph = (LocalInMemoryGraphProvider)tangle.getPersistenceProvider("LOCAL_GRAPH");
    }

    @Override
    public Hash getEntryPoint(int depth) throws Exception {
        Hash ret = null;
        if(BaseIotaConfig.getInstance().getStreamingGraphSupport()) {
            ret = localGraph.getPivotalHash(depth);
        } else {
            buildGraph();
            try {
                KatzCentrality centrality = new KatzCentrality(graph, 0.5);
                score = centrality.compute();
            } catch(Exception e) {
                e.printStackTrace(new PrintStream(System.out));
            }
            ret = getPivotalHash(depth);
        }
        return ret;
    }

    private Hash getPivotalHash(int depth)
    {
        Hash ret = null;
        if(depth == -1) {
            Set<Hash> set = topOrder.get(1);
            ret = set.iterator().next();
            return ret;
        }
        // TODO if the same score, choose randomly
        Set<Hash> hashsOnLevel = topOrder.get(totalDepth-depth);
        double maxScore = 0;
        for(Hash h : hashsOnLevel) {
            if(score.get(h)>=maxScore){
                ret = h;
                maxScore = score.get(h);
            }
        }
        return ret;
    }

    // Get the graph using the BFS method
    private void buildGraph() {
        try {
            Pair<Indexable, Persistable> one = tangle.getFirst(Transaction.class, TransactionHash.class);
            while(one != null && one.low != null) {
                TransactionViewModel model = new TransactionViewModel((Transaction)one.hi, (TransactionHash)one.low);
                Hash trunk = model.getTrunkTransactionHash();
                Hash branch = model.getBranchTransactionHash();

                // approve direction
                if(graph.get(model.getHash()) == null) {
                    graph.put(model.getHash(), new HashSet<Hash>());
                }
                graph.get(model.getHash()).add(trunk);
                graph.get(model.getHash()).add(branch);

                // approved direction
                if(revGraph.get(trunk) == null) {
                    revGraph.put(trunk, new HashSet<Hash>());
                }
                if(revGraph.get(branch) == null) {
                    revGraph.put(branch, new HashSet<Hash>());
                }
                revGraph.get(trunk).add(model.getHash());
                revGraph.get(branch).add(model.getHash());

                // update degrees
                if(degs.get(model.getHash()) == null || degs.get(model.getHash()) == 0) {
                    degs.put(model.getHash(), 2);
                } 
                if(degs.get(trunk) == null) {
                    degs.put(trunk, 0);
                }
                if(degs.get(branch) == null) {
                    degs.put(branch, 0);
                }

                one = tangle.next(Transaction.class, one.low);
            }
            computeToplogicalOrder();
        } catch(Exception e) {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    private void computeToplogicalOrder() {
        Deque<Hash> bfsQ = new ArrayDeque<>();
        Map<Hash, Integer> level = new HashMap<Hash, Integer>();
        Set<Hash> visited = new HashSet<Hash>();

        for(Hash h : degs.keySet()) {
            if(!degs.containsKey(h) || degs.get(h) == 0) {
                bfsQ.addLast(h);
                level.put(h, 0);
                break;
            }
        }

        
        while(!bfsQ.isEmpty()) {
            Hash h = bfsQ.pollFirst();
            int lvl = level.get(h);
            totalDepth = Math.max(totalDepth, lvl+1);
            if(!topOrder.containsKey(lvl)) {
                topOrder.put(lvl, new HashSet<Hash>());
            }            
            topOrder.get(lvl).add(h);

            Set<Hash> out = revGraph.get(h);
            if(out != null) {
                for(Hash o : out) {
                    if(!visited.contains(o)){
                        bfsQ.addLast(o);
                        visited.add(o);
                        level.put(o, lvl+1);
                    }
                }
            }
        }
    }

    // for graphviz visualization
    void printGraph(HashMap<Hash, Set<Hash>> graph)
    {
        for(Hash key : graph.keySet())
        {
            for(Hash val : graph.get(key))
            {
                if(nameMap != null) {
                    System.out.println("\""+nameMap.get(key)+"\"->"+
                                       "\""+nameMap.get(val)+"\"");
                } else {
                    System.out.println("\""+key+"\"->"+
                                       "\""+val+"\"");
                }
            }
        }
    }

    // for graphviz visualization
    void printRevGraph(HashMap<Hash, Set<Hash>> revGraph)
    {
        for(Hash key : revGraph.keySet())
        {
            for(Hash val : revGraph.get(key))
            {
                if(nameMap != null) {
                    System.out.println("\""+nameMap.get(key)+"\"->"+
                                       "\""+nameMap.get(val)+"\"");
                } else {
                    System.out.println("\""+key+"\"->"+
                                       "\""+val+"\"");
                }
            }
        }
    }

    // for graphviz visualization
    void printTopOrder(HashMap<Integer, Set<Hash>> topOrder)
    {
        for(Integer key : topOrder.keySet())
        {
            for(Hash val : topOrder.get(key))
            {
                if(nameMap != null) {
                    System.out.print(nameMap.get(val)+" ");
                } else {
                    System.out.println(val+" ");
                }
            }
            System.out.println();
        }
    }
}
